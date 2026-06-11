package com.notifyflow.dto;

import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationPriority;
import com.notifyflow.model.enums.NotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response body for notification queries and the send acknowledgement.
 * Maps directly from {@link com.notifyflow.model.entity.NotificationEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Notification details response")
public class NotificationResponseDTO {

    @Schema(description = "Notification ID", example = "42")
    private Long id;

    @Schema(description = "Target user ID", example = "1")
    private Long userId;

    @Schema(description = "Delivery channel", example = "EMAIL")
    private NotificationChannel channel;

    @Schema(description = "Notification title",
            example = "Your order has shipped")
    private String title;

    @Schema(description = "Notification body", example = "Order #12345 is on its way!")
    private String message;

    @Schema(description = "Current delivery status", example = "PENDING")
    private NotificationStatus status;

    @Schema(description = "Notification priority", example = "NORMAL")
    private NotificationPriority priority;

    @Schema(description = "Kafka partition offset for traceability", example = "1024")
    private Long kafkaOffset;

    @Schema(description = "Number of delivery retry attempts", example = "0")
    private Integer retryCount;

    @Schema(description = "Timestamp when notification was created",
            example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when notification was delivered — null if pending/failed",
            example = "2024-01-15T10:30:01")
    private LocalDateTime deliveredAt;
}