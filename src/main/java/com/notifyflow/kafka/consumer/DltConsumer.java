package com.notifyflow.kafka.consumer;

import com.notifyflow.kafka.event.NotificationEvent;
import com.notifyflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Dead Letter Topic (DLT) consumer.
 *
 * Consumes from all three DLT topics:
 *   notifyflow.email.dlt
 *   notifyflow.sms.dlt
 *   notifyflow.inapp.dlt
 *
 * Messages arrive here after exhausting all retry attempts
 * in the main consumer (3 retries + 1s backoff per KafkaConfig).
 *
 * Responsibilities:
 *   1. Log the failed event with full context for alerting
 *   2. Mark the notification as FAILED in MySQL
 *   3. Acknowledge the DLT offset (don't retry from DLT automatically)
 *
 * In production, this handler would also:
 *   - Publish to an alerting system (PagerDuty, OpsGenie)
 *   - Write to a separate audit table for failed deliveries
 *   - Trigger a compensating action (e.g. send via fallback channel)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DltConsumer {

    private final NotificationService notificationService;

    /**
     * Consumes messages from all DLT topics.
     *
     * Uses a separate consumer group (notifyflow-dlt-group) so DLT
     * processing doesn't affect the main consumer group's offsets.
     *
     * @param record    the failed Kafka message
     * @param ack       manual acknowledgement handle
     * @param topic     the DLT topic this message arrived from
     * @param partition the partition within the DLT topic
     * @param offset    the offset within the DLT partition
     */
    @KafkaListener(
            topics = {
                    "${app.kafka.topics.email-dlt}",
                    "${app.kafka.topics.sms-dlt}",
                    "${app.kafka.topics.in-app-dlt}"
            },
            groupId          = "notifyflow-dlt-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDlt(
            ConsumerRecord<String, NotificationEvent> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int    partition,
            @Header(KafkaHeaders.OFFSET)             long   offset) {

        NotificationEvent event = record.value();

        // Defensive null check — DLT messages can occasionally have
        // null payloads if the original message failed deserialization
        if (event == null) {
            log.error("DLT received message with NULL payload — " +
                            "topic=[{}] partition=[{}] offset=[{}] " +
                            "rawKey=[{}] — skipping",
                    topic, partition, offset, record.key());
            ack.acknowledge();
            return;
        }

        log.error("DLT message received — NOTIFICATION DELIVERY FAILED PERMANENTLY " +
                        "— notificationId=[{}] userId=[{}] channel=[{}] " +
                        "topic=[{}] partition=[{}] offset=[{}] " +
                        "retryAttempt=[{}]",
                event.getNotificationId(), event.getUserId(),
                event.getChannel(), topic, partition, offset,
                event.getRetryAttempt());

        try {
            // Mark the notification as permanently FAILED in MySQL
            notificationService.markDelivered(
                    event.getNotificationId(), offset, false);

            log.warn("Notification marked FAILED in DB — " +
                    "notificationId=[{}]", event.getNotificationId());

        } catch (Exception ex) {
            // Log but don't rethrow — we don't want DLT processing
            // to loop. The offset will still be committed.
            log.error("Failed to update DB status for DLT message — " +
                            "notificationId=[{}] error=[{}]",
                    event.getNotificationId(), ex.getMessage(), ex);
        } finally {
            // Always acknowledge DLT messages — we never retry from DLT
            // automatically. Manual replay requires explicit tooling.
            ack.acknowledge();
        }
    }
}