package com.notifyflow.kafka.producer;

import com.notifyflow.kafka.event.NotificationEvent;
import com.notifyflow.model.entity.NotificationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes notification events to Kafka topics.
 *
 * Routing logic:
 *   EMAIL  → notifyflow.email
 *   SMS    → notifyflow.sms
 *   IN_APP → notifyflow.inapp
 *
 * Partitioning strategy:
 *   Key = String.valueOf(userId)
 *   Kafka's default partitioner hashes the key, so all notifications
 *   for the same user land on the same partition — preserving
 *   per-user ordering guarantees.
 *
 * Send model:
 *   Non-blocking (CompletableFuture). The calling service thread
 *   is not blocked waiting for broker acknowledgement.
 *   Success/failure callbacks update the DB asynchronously.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Value("${app.kafka.topics.email}")
    private String emailTopic;

    @Value("${app.kafka.topics.sms}")
    private String smsTopic;

    @Value("${app.kafka.topics.in-app}")
    private String inAppTopic;

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Builds a NotificationEvent from the persisted entity and
     * publishes it to the appropriate Kafka topic.
     *
     * The entity is used (not the DTO) because by this point the
     * notification has been saved and has a database-assigned ID.
     * Consumers need that ID to update delivery status.
     *
     * @param entity the persisted NotificationEntity
     */
    public void publishNotification(NotificationEntity entity) {
        String topic = resolveTopic(entity);
        String key   = String.valueOf(entity.getUser().getId());

        NotificationEvent event = buildEvent(entity);

        log.info("Publishing notification to Kafka — " +
                        "topic=[{}] key=[{}] notificationId=[{}]",
                topic, key, entity.getId());

        CompletableFuture<SendResult<String, NotificationEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                handleSuccess(result, entity.getId());
            } else {
                handleFailure(ex, entity.getId(), topic);
            }
        });
    }

    /**
     * Publishes a notification event directly (used for retries
     * where the event is already constructed).
     *
     * @param topic the target Kafka topic
     * @param key   the partition key (typically userId)
     * @param event the pre-built notification event
     */
    public void publishEvent(String topic,
                             String key,
                             NotificationEvent event) {
        log.debug("Re-publishing event — topic=[{}] key=[{}] " +
                        "notificationId=[{}] attempt=[{}]",
                topic, key, event.getNotificationId(),
                event.getRetryAttempt());

        CompletableFuture<SendResult<String, NotificationEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                handleSuccess(result, event.getNotificationId());
            } else {
                handleFailure(ex, event.getNotificationId(), topic);
            }
        });
    }

    // ── Internal helpers ───────────────────────────────────────────

    /**
     * Resolves the Kafka topic name from the notification channel.
     *
     * @param entity the notification entity
     * @return Kafka topic name
     */
    private String resolveTopic(NotificationEntity entity) {
        return switch (entity.getChannel()) {
            case EMAIL  -> emailTopic;
            case SMS    -> smsTopic;
            case IN_APP -> inAppTopic;
        };
    }

    /**
     * Builds a NotificationEvent from a persisted NotificationEntity.
     * Sets eventCreatedAt to now for latency tracking.
     *
     * @param entity the source entity
     * @return the Kafka event payload
     */
    private NotificationEvent buildEvent(NotificationEntity entity) {
        return NotificationEvent.builder()
                .notificationId(entity.getId())
                .userId(entity.getUser().getId())
                .channel(entity.getChannel())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .priority(entity.getPriority())
                .eventCreatedAt(LocalDateTime.now())
                .retryAttempt(0)
                .build();
    }

    /**
     * Callback on successful broker acknowledgement.
     * Logs partition and offset for traceability.
     *
     * @param result         the send result from Kafka
     * @param notificationId the notification ID for correlation
     */
    private void handleSuccess(
            SendResult<String, NotificationEvent> result,
            Long notificationId) {

        int    partition = result.getRecordMetadata().partition();
        long   offset    = result.getRecordMetadata().offset();
        String topic     = result.getRecordMetadata().topic();

        log.info("Kafka publish confirmed — notificationId=[{}] " +
                        "topic=[{}] partition=[{}] offset=[{}]",
                notificationId, topic, partition, offset);
    }

    /**
     * Callback on Kafka publish failure.
     * Logs the error — the DeadLetterPublishingRecoverer in
     * KafkaConfig handles retry exhaustion at the consumer side.
     *
     * Note: Producer failures (broker unreachable) are separate from
     * consumer failures (processing error). Producer retries are
     * configured via ProducerConfig.RETRIES_CONFIG = 3 in KafkaConfig.
     *
     * @param ex             the exception from the failed send
     * @param notificationId the notification ID for correlation
     * @param topic          the target topic that failed
     */
    private void handleFailure(Throwable ex,
                               Long notificationId,
                               String topic) {
        log.error("Kafka publish FAILED — notificationId=[{}] topic=[{}] " +
                "error=[{}]", notificationId, topic, ex.getMessage(), ex);
    }
}