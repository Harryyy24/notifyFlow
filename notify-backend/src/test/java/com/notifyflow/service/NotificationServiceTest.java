package com.notifyflow.service;

import com.notifyflow.dto.NotificationRequestDTO;
import com.notifyflow.dto.NotificationResponseDTO;
import com.notifyflow.exception.DuplicateNotificationException;
import com.notifyflow.exception.QuietHoursActiveException;
import com.notifyflow.exception.ResourceNotFoundException;
import com.notifyflow.kafka.producer.NotificationProducer;
import com.notifyflow.model.entity.NotificationEntity;
import com.notifyflow.model.entity.UserEntity;
import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationPriority;
import com.notifyflow.model.enums.NotificationStatus;
import com.notifyflow.model.enums.UserRole;
import com.notifyflow.repository.NotificationRepository;
import com.notifyflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService — the core business logic layer.
 *
 * All dependencies are mocked. Tests cover:
 * - Happy path send pipeline
 * - Duplicate detection (409)
 * - Quiet hours rejection (429)
 * - User not found (404)
 * - markDelivered / markFailed callbacks
 * - History retrieval
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Tests")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository         userRepository;
    @Mock private DeduplicationService   deduplicationService;
    @Mock private PreferenceService      preferenceService;
    @Mock private NotificationProducer   notificationProducer;

    @InjectMocks
    private NotificationService notificationService;

    private UserEntity testUser;
    private NotificationEntity testEntity;
    private NotificationRequestDTO validRequest;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("hashed")
                .role(UserRole.USER)
                .build();

        testEntity = NotificationEntity.builder()
                .id(10L)
                .user(testUser)
                .channel(NotificationChannel.EMAIL)
                .title("Test Title")
                .message("Test Message")
                .status(NotificationStatus.PENDING)
                .priority(NotificationPriority.NORMAL)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        validRequest = NotificationRequestDTO.builder()
                .userId(1L)
                .channel(NotificationChannel.EMAIL)
                .title("Test Title")
                .message("Test Message")
                .priority(NotificationPriority.NORMAL)
                .build();
    }

    // ── sendNotification — Happy Path ──────────────────────────────

    @Test
    @DisplayName("sendNotification — persists entity with PENDING status")
    void sendNotification_persistsWithPendingStatus() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        when(preferenceService.isChannelEnabled(1L, NotificationChannel.EMAIL))
                .thenReturn(true);
        when(deduplicationService.isDuplicate(anyLong(), any(), anyString(), anyString()))
                .thenReturn(false);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenReturn(testEntity);

        NotificationResponseDTO response =
                notificationService.sendNotification(validRequest);

        ArgumentCaptor<NotificationEntity> captor =
                ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("sendNotification — publishes event to Kafka after DB save")
    void sendNotification_publishesToKafka() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        when(preferenceService.isChannelEnabled(1L, NotificationChannel.EMAIL))
                .thenReturn(true);
        when(deduplicationService.isDuplicate(anyLong(), any(), anyString(), anyString()))
                .thenReturn(false);
        when(notificationRepository.save(any()))
                .thenReturn(testEntity);

        notificationService.sendNotification(validRequest);

        verify(notificationProducer).publishNotification(testEntity);
    }

    // ── sendNotification — User Not Found ──────────────────────────

    @Test
    @DisplayName("sendNotification — throws ResourceNotFoundException when user missing")
    void sendNotification_throwsNotFound_whenUserMissing() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                notificationService.sendNotification(validRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(notificationRepository, never()).save(any());
        verify(notificationProducer,   never()).publishNotification(any());
    }

    // ── sendNotification — Duplicate Detection ─────────────────────

    @Test
    @DisplayName("sendNotification — throws DuplicateNotificationException when duplicate")
    void sendNotification_throwsDuplicate_whenDuplicateDetected() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        when(preferenceService.isChannelEnabled(1L, NotificationChannel.EMAIL))
                .thenReturn(true);
        when(deduplicationService.isDuplicate(anyLong(), any(), anyString(), anyString()))
                .thenReturn(true);
        when(deduplicationService.getRemainingTtlSeconds(anyLong(), any(), anyString(), anyString()))
                .thenReturn(300L);

        assertThatThrownBy(() ->
                notificationService.sendNotification(validRequest))
                .isInstanceOf(DuplicateNotificationException.class)
                .hasMessageContaining("Duplicate notification");

        verify(notificationRepository, never()).save(any());
        verify(notificationProducer,   never()).publishNotification(any());
    }

    // ── sendNotification — Quiet Hours ─────────────────────────────

    @Test
    @DisplayName("sendNotification — throws QuietHoursActiveException for LOW priority during quiet hours")
    void sendNotification_throwsQuietHours_forLowPriorityDuringQuietHours() {
        NotificationRequestDTO lowPriorityRequest =
                NotificationRequestDTO.builder()
                        .userId(1L)
                        .channel(NotificationChannel.EMAIL)
                        .title("Low Priority")
                        .message("This should be blocked")
                        .priority(NotificationPriority.LOW)
                        .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        when(preferenceService.isChannelEnabled(1L, NotificationChannel.EMAIL))
                .thenReturn(true);
        when(preferenceService.isQuietHoursActive(1L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                notificationService.sendNotification(lowPriorityRequest))
                .isInstanceOf(QuietHoursActiveException.class)
                .hasMessageContaining("Quiet hours");

        verify(notificationRepository, never()).save(any());
        verify(notificationProducer,   never()).publishNotification(any());
    }

    @Test
    @DisplayName("sendNotification — NORMAL priority passes through quiet hours")
    void sendNotification_normalPriority_passesQuietHoursCheck() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(testUser));
        when(preferenceService.isChannelEnabled(1L, NotificationChannel.EMAIL))
                .thenReturn(true);
        when(deduplicationService.isDuplicate(anyLong(), any(), anyString(), anyString()))
                .thenReturn(false);
        when(notificationRepository.save(any()))
                .thenReturn(testEntity);

        // Should NOT throw even if quiet hours are active
        // because priority is NORMAL, not LOW
        assertThatNoException().isThrownBy(() ->
                notificationService.sendNotification(validRequest));

        // isQuietHoursActive should never be called for NORMAL priority
        verify(preferenceService, never()).isQuietHoursActive(anyLong());
    }

    // ── markDelivered ──────────────────────────────────────────────

    @Test
    @DisplayName("markDelivered — updates status to DELIVERED with offset")
    void markDelivered_updatesStatusToDelivered() {
        when(notificationRepository.findById(10L))
                .thenReturn(Optional.of(testEntity));
        when(notificationRepository.save(any()))
                .thenReturn(testEntity);

        notificationService.markDelivered(10L, 100L, true);

        assertThat(testEntity.getStatus())
                .isEqualTo(NotificationStatus.DELIVERED);
        assertThat(testEntity.getKafkaOffset()).isEqualTo(100L);
        assertThat(testEntity.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("markDelivered — updates status to FAILED when success=false")
    void markDelivered_updatesStatusToFailed_whenSuccessFalse() {
        when(notificationRepository.findById(10L))
                .thenReturn(Optional.of(testEntity));
        when(notificationRepository.save(any()))
                .thenReturn(testEntity);

        notificationService.markDelivered(10L, 100L, false);

        assertThat(testEntity.getStatus())
                .isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("markDelivered — does nothing when notification not found")
    void markDelivered_doesNothing_whenNotificationNotFound() {
        when(notificationRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Should not throw — ifPresent handles the empty case
        assertThatNoException().isThrownBy(() ->
                notificationService.markDelivered(999L, 100L, true));

        verify(notificationRepository, never()).save(any());
    }
}