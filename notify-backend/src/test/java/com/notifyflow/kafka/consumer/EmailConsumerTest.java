package com.notifyflow.kafka.consumer;

import com.notifyflow.kafka.event.NotificationEvent;
import com.notifyflow.model.entity.UserEntity;
import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationPriority;
import com.notifyflow.model.enums.UserRole;
import com.notifyflow.repository.UserRepository;
import com.notifyflow.service.EmailSenderService;
import com.notifyflow.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailConsumer Tests")
class EmailConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private EmailConsumer emailConsumer;

    private NotificationEvent testEvent;
    private ConsumerRecord<String, NotificationEvent> consumerRecord;
    private UserEntity testUser;

    private static final int PARTITION = 0;
    private static final long OFFSET   = 100L;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(42L)
                .name("Test User")
                .email("testuser@example.com")
                .role(UserRole.USER)
                .build();

        testEvent = NotificationEvent.builder()
                .notificationId(1L)
                .userId(42L)
                .channel(NotificationChannel.EMAIL)
                .title("Test Email")
                .message("Test email body")
                .priority(NotificationPriority.NORMAL)
                .eventCreatedAt(LocalDateTime.now())
                .retryAttempt(0)
                .build();

        consumerRecord = new ConsumerRecord<>(
                "notifyflow.email",
                PARTITION,
                OFFSET,
                "42",
                testEvent
        );
    }

    @Test
    @DisplayName("consume — looks up user and sends email on happy path")
    void consume_marksDeliveredOnSuccess() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));

        emailConsumer.consume(
                consumerRecord, acknowledgment, PARTITION, OFFSET);

        verify(emailSenderService).sendEmail(
                "testuser@example.com", "Test Email", "Test email body");
        verify(notificationService).markDelivered(
                eq(1L), eq(OFFSET), eq(true));
    }

    @Test
    @DisplayName("consume — acknowledges offset after successful processing")
    void consume_acknowledgesOffsetOnSuccess() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));

        emailConsumer.consume(
                consumerRecord, acknowledgment, PARTITION, OFFSET);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consume — does not acknowledge if markDelivered throws")
    void consume_doesNotAcknowledge_whenServiceThrows() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
        doThrow(new RuntimeException("DB unavailable"))
                .when(notificationService)
                .markDelivered(anyLong(), anyLong(), anyBoolean());

        assertThatThrownBy(() ->
                emailConsumer.consume(
                        consumerRecord, acknowledgment, PARTITION, OFFSET))
                .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("consume — propagates exception for retry handler when mail fails")
    void consume_propagatesException_whenMailFails() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(emailSenderService).sendEmail(anyString(), anyString(), anyString());

        assertThatThrownBy(() ->
                emailConsumer.consume(
                        consumerRecord, acknowledgment, PARTITION, OFFSET))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email delivery failed");
    }

    @Test
    @DisplayName("consume — passes correct offset to markDelivered")
    void consume_passesCorrectOffsetToMarkDelivered() {
        long specificOffset = 999L;
        when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));

        ConsumerRecord<String, NotificationEvent> recordWithOffset =
                new ConsumerRecord<>(
                        "notifyflow.email", PARTITION, specificOffset, "42", testEvent);

        emailConsumer.consume(
                recordWithOffset, acknowledgment, PARTITION, specificOffset);

        verify(notificationService).markDelivered(
                eq(1L), eq(specificOffset), eq(true));
    }

    @Test
    @DisplayName("consume — markDelivered called exactly once per message")
    void consume_callsMarkDeliveredExactlyOnce() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));

        emailConsumer.consume(
                consumerRecord, acknowledgment, PARTITION, OFFSET);

        verify(notificationService, times(1))
                .markDelivered(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("consume — throws when user not found")
    void consume_throwsWhenUserNotFound() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                emailConsumer.consume(
                        consumerRecord, acknowledgment, PARTITION, OFFSET))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email delivery failed");

        verify(notificationService, never())
                .markDelivered(anyLong(), anyLong(), anyBoolean());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("consume — does not acknowledge when email sending fails")
    void consume_doesNotAcknowledge_whenEmailFails() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(emailSenderService).sendEmail(anyString(), anyString(), anyString());

        assertThatThrownBy(() ->
                emailConsumer.consume(
                        consumerRecord, acknowledgment, PARTITION, OFFSET))
                .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
        verify(notificationService, never())
                .markDelivered(anyLong(), anyLong(), anyBoolean());
    }
}
