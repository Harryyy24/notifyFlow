package com.notifyflow.model.entity;

import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationPriority;
import com.notifyflow.model.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a single notification event.
 *
 * Acts as the permanent audit record for every notification
 * regardless of delivery outcome. Status is updated asynchronously
 * by Kafka consumers after processing.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_created",
                        columnList = "user_id, created_at DESC"),
                @Index(name = "idx_notifications_status",
                        columnList = "status"),
                @Index(name = "idx_notifications_channel",
                        columnList = "channel")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // ── Lifecycle ──────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Domain helpers ─────────────────────────────────────────────

    /**
     * Transitions this notification to DELIVERED state.
     * Sets deliveredAt timestamp atomically with status change.
     */
    public void markDelivered(Long kafkaOffset) {
        this.status      = NotificationStatus.DELIVERED;
        this.kafkaOffset = kafkaOffset;
        this.deliveredAt = LocalDateTime.now();
    }

    /**
     * Transitions this notification to FAILED state.
     * Increments retry count for observability.
     */
    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.retryCount++;
    }

    /**
     * Increments the retry counter without changing status.
     * Called by the consumer error handler on each retry attempt.
     */
    public void incrementRetry() {
        this.retryCount++;
    }
}