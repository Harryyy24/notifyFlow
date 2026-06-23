package com.notifyflow.kafka.consumer;

import com.notifyflow.kafka.event.NotificationEvent;
import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationPriority;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailConsumer.
 *
 * NotificationService and Acknowledgment are mocked.
 * Kafka broker not required — consumer logic tested in isolation.
 *
 * Note: failureRate is set to 0.0 in all tests for determinism.
 * The random failure path is covered by the integration test profile.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailConsumer Tests")
class EmailConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private EmailConsumer emailConsumer;

    private NotificationEvent testEvent;
    private ConsumerRecord<String, NotificationEvent> consumerRecord;

    private static final int PARTITION = 0;
    private static final long OFFSET   = 100L;

    @BeforeEach
    void setUp() {
        // Disable random failures for deterministic tests
        ReflectionTestUtils.setField(emailConsumer, "failureRate", 0.0);

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

    // ── Successful Processing ──────────────────────────────────────

    @Test
    @DisplayName("consume — calls markDelivered with success=true on happy path")
    void consume_marksDeliveredOnSuccess() throws Exception {
        emailConsumer.consume(
                consumerRecord, acknowledgment, PARTITION, OFFSET);

        verify(notificationService).markDelivered(
                eq(1L), eq(OFFSET), eq(true));
    }

    @Test
    @DisplayName("consume — acknowledges offset after successful processing")
    void consume_acknowledgesOffsetOnSuccess() throws Exception {
        emailConsumer.consume(
                consumerRecord, acknowledgment, PARTITION, OFFSET);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consume — does not acknowledge if markDelivered throws")
    void consume_doesNotAcknowledge_whenServiceThrows() {
        doThrow(new RuntimeException("DB unavailable"))
                .when(notificationService)
                .markDelivered(anyLong(), anyLong(), anyBoolean());

        assertThatThrownBy(() ->
                emailConsumer.consume(
                        consumerRecord, acknowledgment, PARTITION, OFFSET))
                .isInstanceOf(RuntimeException.class);

        // Offset must NOT be committed — message should be retried
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("consume — propagates exception for retry handler on failure")
    void consume_propagatesException_forRetryHandler() {
        // Set failure rate to 100% to force failure path
        ReflectionTestUtils.setField(emailConsumer, "failureRate", 1.0);

        assertThatThrownBy(() ->
                emailConsumer.consume(
                        consumerRecord, acknowledgment, PARTITION, OFFSET))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated email delivery failure");
    }

    @Test
    @DisplayName("consume — passes correct offset to markDelivered")
    void consume_passesCorrectOffsetToMarkDelivered() throws Exception {
        long specificOffset = 999L;

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
    void consume_callsMarkDeliveredExactlyOnce() throws Exception {
        emailConsumer.consume(
                consumerRecord, acknowledgment, PARTITION, OFFSET);

        verify(notificationService, times(1))
                .markDelivered(anyLong(), anyLong(), anyBoolean());
    }
}