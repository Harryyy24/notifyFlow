package com.notifyflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standardised error response body returned by GlobalExceptionHandler.
 * All 4xx and 5xx responses use this structure.
 *
 * Example:
 * {
 *   "status": 409,
 *   "error": "Conflict",
 *   "message": "Duplicate notification detected within deduplication window",
 *   "timestamp": "2024-01-15T10:30:00"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard error response")
public class ErrorResponseDTO {

    @Schema(description = "HTTP status code", example = "409")
    private int status;

    @Schema(description = "HTTP status reason phrase", example = "Conflict")
    private String error;

    @Schema(description = "Human-readable error detail",
            example = "Duplicate notification detected within deduplication window")
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Timestamp of the error", example = "2024-01-15T10:30:00")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Schema(description = "Request path that triggered the error",
            example = "/api/notifications/send")
    private String path;
}