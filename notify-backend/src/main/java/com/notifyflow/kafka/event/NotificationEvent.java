package com.notifyflow.kafka.event;

import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka message payload published to notification topics.
 *
 * This class lives in kafka.event (not model.entity) because it is
 * a messaging contract, not a persistence contract. Keeping them
 * separate means you can evolve the DB schema and the Kafka schema
 * independently — a critical design principle in event-driven systems.
 *
 * Serialized as JSON by JsonSerializer (configured in KafkaConfig).
 * Must have a no-args constructor for Jackson deserialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /**
     * Database ID of the NotificationEntity created before publishing.
     * Consumers use this to update the status after processing.
     */
    private Long notificationId;

    /** Target user's database ID. */
    private Long userId;

    /** Delivery channel — determines which topic this event is routed to. */
    private NotificationChannel channel;

    /** Notification title. */
    private String title;

    /** Notification message body. */
    private String message;

    /** Priority — consumers may use this for processing order hints. */
    private NotificationPriority priority;

    /**
     * Timestamp when the event was created at the producer.
     * Useful for measuring end-to-end latency in monitoring.
     */
    private LocalDateTime eventCreatedAt;

    /**
     * Retry attempt counter — incremented by the error handler
     * before re-publishing. Consumers can use this for logging.
     */
    @Builder.Default
    private int retryAttempt = 0;
}