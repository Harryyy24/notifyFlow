package com.notifyflow.kafka.producer;

import com.notifyflow.kafka.event.NotificationEvent;
import com.notifyflow.model.entity.NotificationEntity;
import com.notifyflow.model.entity.UserEntity;
import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationPriority;
import com.notifyflow.model.enums.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationProducer.
 *
 * KafkaTemplate is mocked — no broker connection needed.
 * Tests verify topic routing, key strategy, and event construction.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationProducer Tests")
class NotificationProducerTest {

    @Mock
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @InjectMocks
    private NotificationProducer notificationProducer;

    private NotificationEntity emailNotification;
    private NotificationEntity smsNotification;
    private NotificationEntity inAppNotification;

    @BeforeEach
    void setUp() {
        // Inject topic name @Value fields
        ReflectionTestUtils.setField(
                notificationProducer, "emailTopic",  "notifyflow.email");
        ReflectionTestUtils.setField(
                notificationProducer, "smsTopic",    "notifyflow.sms");
        ReflectionTestUtils.setField(
                notificationProducer, "inAppTopic",  "notifyflow.inapp");

        UserEntity user = UserEntity.builder()
                .id(42L)
                .name("Test User")
                .email("test@example.com")
                .build();

        emailNotification = NotificationEntity.builder()
                .id(1L)
                .user(user)
                .channel(NotificationChannel.EMAIL)
                .title("Email Title")
                .message("Email Message")
                .priority(NotificationPriority.NORMAL)
                .status(NotificationStatus.PENDING)
                .build();

        smsNotification = NotificationEntity.builder()
                .id(2L)
                .user(user)
                .channel(NotificationChannel.SMS)
                .title("SMS Title")
                .message("SMS Message")
                .priority(NotificationPriority.HIGH)
                .status(NotificationStatus.PENDING)
                .build();

        inAppNotification = NotificationEntity.builder()
                .id(3L)
                .user(user)
                .channel(NotificationChannel.IN_APP)
                .title("InApp Title")
                .message("InApp Message")
                .priority(NotificationPriority.LOW)
                .status(NotificationStatus.PENDING)
                .build();

        // Return completed future for all kafka sends
        SendResult<String, NotificationEvent> mockResult =
                mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata mockMeta =
                mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(mockResult.getRecordMetadata()).thenReturn(mockMeta);
        when(mockMeta.partition()).thenReturn(0);
        when(mockMeta.offset()).thenReturn(100L);
        when(mockMeta.topic()).thenReturn("notifyflow.email");

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResult));
    }

    // ── Topic Routing ──────────────────────────────────────────────

    @Test
    @DisplayName("publishNotification — routes EMAIL to notifyflow.email topic")
    void publishNotification_routesEmailToCorrectTopic() {
        notificationProducer.publishNotification(emailNotification);

        verify(kafkaTemplate).send(
                eq("notifyflow.email"), anyString(), any(NotificationEvent.class));
    }

    @Test
    @DisplayName("publishNotification — routes SMS to notifyflow.sms topic")
    void publishNotification_routesSmsToCorrectTopic() {
        notificationProducer.publishNotification(smsNotification);

        verify(kafkaTemplate).send(
                eq("notifyflow.sms"), anyString(), any(NotificationEvent.class));
    }

    @Test
    @DisplayName("publishNotification — routes IN_APP to notifyflow.inapp topic")
    void publishNotification_routesInAppToCorrectTopic() {
        notificationProducer.publishNotification(inAppNotification);

        verify(kafkaTemplate).send(
                eq("notifyflow.inapp"), anyString(), any(NotificationEvent.class));
    }

    // ── Partition Key ──────────────────────────────────────────────

    @Test
    @DisplayName("publishNotification — uses userId as partition key")
    void publishNotification_usesUserIdAsPartitionKey() {
        notificationProducer.publishNotification(emailNotification);

        verify(kafkaTemplate).send(
                anyString(),
                eq("42"),           // userId as String
                any(NotificationEvent.class));
    }

    // ── Event Construction ─────────────────────────────────────────

    @Test
    @DisplayName("publishNotification — event contains correct notificationId")
    void publishNotification_eventContainsCorrectNotificationId() {
        ArgumentCaptor<NotificationEvent> captor =
                ArgumentCaptor.forClass(NotificationEvent.class);

        notificationProducer.publishNotification(emailNotification);

        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        NotificationEvent captured = captor.getValue();
        assertThat(captured.getNotificationId()).isEqualTo(1L);
        assertThat(captured.getUserId()).isEqualTo(42L);
        assertThat(captured.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(captured.getTitle()).isEqualTo("Email Title");
        assertThat(captured.getMessage()).isEqualTo("Email Message");
        assertThat(captured.getPriority()).isEqualTo(NotificationPriority.NORMAL);
    }

    @Test
    @DisplayName("publishNotification — event has retryAttempt of 0 on first send")
    void publishNotification_eventHasZeroRetryAttempt() {
        ArgumentCaptor<NotificationEvent> captor =
                ArgumentCaptor.forClass(NotificationEvent.class);

        notificationProducer.publishNotification(emailNotification);

        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        assertThat(captor.getValue().getRetryAttempt()).isZero();
    }

    @Test
    @DisplayName("publishNotification — event has non-null eventCreatedAt")
    void publishNotification_eventHasCreatedAt() {
        ArgumentCaptor<NotificationEvent> captor =
                ArgumentCaptor.forClass(NotificationEvent.class);

        notificationProducer.publishNotification(emailNotification);

        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        assertThat(captor.getValue().getEventCreatedAt()).isNotNull();
    }
}