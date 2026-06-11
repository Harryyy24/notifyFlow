package com.notifyflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * DTO for both GET and PUT /api/preferences/{userId}.
 * Used for reading and writing user notification preferences.
 *
 * quietHoursStart / quietHoursEnd are optional.
 * If both are null, quiet hours are disabled for the user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User notification preferences")
public class PreferenceDTO {

    @Schema(description = "User ID this preference belongs to",
            example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long userId;

    @Schema(description = "Whether email notifications are enabled",
            example = "true")
    private Boolean emailEnabled;

    @Schema(description = "Whether SMS notifications are enabled",
            example = "false")
    private Boolean smsEnabled;

    @Schema(description = "Whether in-app notifications are enabled",
            example = "true")
    private Boolean inAppEnabled;

    /**
     * Quiet hours start time in HH:mm format.
     * LOW priority notifications are blocked during this window.
     */
    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "Quiet hours start — LOW priority blocked after this time",
            example = "22:00",
            type = "string",
            pattern = "HH:mm")
    private LocalTime quietHoursStart;

    /**
     * Quiet hours end time in HH:mm format.
     * Supports overnight windows (e.g. 22:00–08:00).
     */
    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "Quiet hours end — LOW priority unblocked after this time",
            example = "08:00",
            type = "string",
            pattern = "HH:mm")
    private LocalTime quietHoursEnd;
}