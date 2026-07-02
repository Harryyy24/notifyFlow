package com.notifyflow.dto;

import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/notifications/send
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Notification send request")
public class NotificationRequestDTO {

    @NotNull(message = "userId is required")
    @Positive(message = "userId must be a positive number")
    @Schema(description = "Target user ID", example = "1")
    private Long userId;

    @NotNull(message = "channel is required")
    @Schema(description = "Delivery channel",
            example = "EMAIL",
            allowableValues = {"EMAIL", "SMS", "IN_APP"})
    private NotificationChannel channel;

    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title must not exceed 255 characters")
    @Schema(description = "Notification title",
            example = "Your order has shipped")
    private String title;

    @NotBlank(message = "message is required")
    @Size(max = 5000, message = "message must not exceed 5000 characters")
    @Schema(description = "Notification body text",
            example = "Your order #12345 is on its way!")
    private String message;

    @NotNull(message = "priority is required")
    @Schema(description = "Delivery priority — LOW notifications are suppressed during quiet hours",
            example = "NORMAL",
            allowableValues = {"HIGH", "NORMAL", "LOW"})
    private NotificationPriority priority;
}