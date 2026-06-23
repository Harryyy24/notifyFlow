package com.notifyflow.kafka.consumer;

import com.notifyflow.kafka.event.NotificationEvent;
import com.notifyflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kafka consumer for EMAIL channel notifications.
 *
 * Listens on: notifyflow.email (3 partitions)
 * Consumer group: notifyflow-consumer-group
 * Concurrency: 3 threads (one per partition — set in KafkaConfig)
 *
 * Processing model:
 *   1. Receive NotificationEvent from Kafka
 *   2. Simulate email delivery (200–500ms random latency)
 *   3. Simulate 10% random failure rate (real-world approximation)
 *   4. Update MySQL delivery status via NotificationService
 *   5. Manually acknowledge offset (MANUAL_IMMEDIATE mode)
 *
 * Error handling:
 *   DefaultErrorHandler in KafkaConfig retries 3x with 1s backoff.
 *   After exhaustion, DeadLetterPublishingRecoverer routes to
 *   notifyflow.email.dlt. This consumer does NOT catch exceptions —
 *   it lets them propagate to the error handler intentionally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

    private final NotificationService notificationService;

    @Value("${app.notification.failure-rate}")
    private double failureRate;

    private static final int MIN_PROCESSING_MS = 200;
    private static final int MAX_PROCESSING_MS = 500;

    /**
     * Processes an email notification event.
     *
     * @param record  the full Kafka ConsumerRecord (includes offset metadata)
     * @param ack     manual acknowledgement handle
     * @param partition the partition this message was consumed from
     * @param offset    the message offset within the partition
     */
    @KafkaListener(
            topics         = "${app.kafka.topics.email}",
            groupId        = "notifyflow-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, NotificationEvent> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset) {

        NotificationEvent event = record.value();

        log.info("EMAIL consumer received — notificationId=[{}] " +
                        "userId=[{}] partition=[{}] offset=[{}]",
                event.getNotificationId(), event.getUserId(),
                partition, offset);

        try {
            // Step 1 — Simulate email delivery latency
            simulateProcessing();

            // Step 2 — Simulate random failure rate
            if (shouldSimulateFailure()) {
                throw new RuntimeException(
                        "Simulated email delivery failure for " +
                                "notificationId=[" + event.getNotificationId() + "]");
            }

            // Step 3 — Mark as delivered in MySQL
            notificationService.markDelivered(
                    event.getNotificationId(), offset, true);

            // Step 4 — Commit offset only after successful processing
            ack.acknowledge();

            log.info("EMAIL delivered successfully — notificationId=[{}] " +
                            "userId=[{}] offset=[{}]",
                    event.getNotificationId(), event.getUserId(), offset);

        } catch (InterruptedException ie) {
            // Restore interrupt flag and rethrow for error handler
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "Email processing interrupted for notificationId=[" +
                            event.getNotificationId() + "]", ie);
        }
        // RuntimeException propagates to DefaultErrorHandler → retry → DLT
    }

    // ── Simulation Helpers ─────────────────────────────────────────

    /**
     * Simulates email service latency (200–500ms).
     * In production, this is replaced by an actual email client call
     * (e.g. AWS SES, SendGrid SDK).
     */
    private void simulateProcessing() throws InterruptedException {
        long delay = ThreadLocalRandom.current()
                .nextLong(MIN_PROCESSING_MS, MAX_PROCESSING_MS + 1);
        Thread.sleep(delay);
        log.debug("EMAIL processing simulated — delay=[{}ms]", delay);
    }

    /**
     * Returns true ~10% of the time to simulate real-world
     * delivery failures (bounced emails, provider errors, etc.)
     *
     * failureRate is 0.0 in the test profile — see application-test.yml
     */
    private boolean shouldSimulateFailure() {
        return ThreadLocalRandom.current().nextDouble() < failureRate;
    }
}