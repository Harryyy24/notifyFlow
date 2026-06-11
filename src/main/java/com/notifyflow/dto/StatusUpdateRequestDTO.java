package com.notifyflow.dto;

import com.notifyflow.model.enums.NotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for PATCH /api/notifications/{id}/status (ADMIN only).
 * Allows manual override of notification delivery status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Manual status update request (ADMIN only)")
public class StatusUpdateRequestDTO {

    @NotNull(message = "status is required")
    @Schema(description = "New delivery status",
            example = "DELIVERED",
            allowableValues = {"DELIVERED", "FAILED"})
    private NotificationStatus status;
}