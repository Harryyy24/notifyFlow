package com.notifyflow.controller;

import com.notifyflow.dto.NotificationRequestDTO;
import com.notifyflow.dto.NotificationResponseDTO;
import com.notifyflow.dto.PagedResponseDTO;
import com.notifyflow.dto.StatusUpdateRequestDTO;
import com.notifyflow.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Notification management endpoints — all require Bearer JWT.
 *
 * POST   /api/notifications/send               — send a notification
 * GET    /api/notifications/{userId}/history   — paginated history
 * GET    /api/notifications/{id}/status        — single notification status
 * PATCH  /api/notifications/{id}/status        — manual status update (ADMIN)
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications",
        description = "Send notifications and query delivery history/status")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    // ── Send ───────────────────────────────────────────────────────

    @Operation(
            summary     = "Send a notification",
            description = """
            Submits a notification for async delivery via Kafka.
            
            Pipeline:
            1. Validates the request
            2. Checks user channel preferences
            3. Enforces quiet hours for LOW priority
            4. Deduplicates within a 10-minute Redis window
            5. Persists to MySQL with PENDING status
            6. Publishes to the appropriate Kafka topic
            7. Returns 202 Accepted with the notification ID
            
            The actual delivery (DELIVERED/FAILED) happens
            asynchronously via the Kafka consumer.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202",
                    description  = "Notification accepted for async delivery",
                    content = @Content(schema =
                    @Schema(implementation = NotificationResponseDTO.class))),
            @ApiResponse(responseCode = "400",
                    description  = "Validation error — missing or invalid fields"),
            @ApiResponse(responseCode = "401",
                    description  = "Missing or invalid Bearer token"),
            @ApiResponse(responseCode = "404",
                    description  = "User not found"),
            @ApiResponse(responseCode = "409",
                    description  = "Duplicate notification within dedup window"),
            @ApiResponse(responseCode = "429",
                    description  = "Quiet hours active — LOW priority suppressed")
    })
    @PostMapping("/send")
    public ResponseEntity<NotificationResponseDTO> send(
            @Valid @RequestBody NotificationRequestDTO request) {

        log.info("Send request — userId=[{}] channel=[{}] priority=[{}]",
                request.getUserId(), request.getChannel(), request.getPriority());

        NotificationResponseDTO response =
                notificationService.sendNotification(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── History ────────────────────────────────────────────────────

    @Operation(
            summary     = "Get notification history for a user",
            description = "Returns a paginated list of all notifications " +
                    "for the given user, newest first. " +
                    "Results are cached in Redis for 5 minutes."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description  = "History retrieved successfully"),
            @ApiResponse(responseCode = "401",
                    description  = "Missing or invalid Bearer token"),
            @ApiResponse(responseCode = "404",
                    description  = "User not found")
    })
    @GetMapping("/{userId}/history")
    public ResponseEntity<PagedResponseDTO<NotificationResponseDTO>> getHistory(

            @Parameter(description = "Target user ID", example = "1")
            @PathVariable Long userId,

            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be >= 0")
            int page,

            @Parameter(description = "Page size (1–100)", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1,   message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size) {

        log.debug("History request — userId=[{}] page=[{}] size=[{}]",
                userId, page, size);

        PagedResponseDTO<NotificationResponseDTO> response =
                notificationService.getHistory(userId, page, size);

        return ResponseEntity.ok(response);
    }

    // ── Status ─────────────────────────────────────────────────────

    @Operation(
            summary     = "Get delivery status of a notification",
            description = "Returns the current status (PENDING/DELIVERED/FAILED) " +
                    "and full details of a single notification."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description  = "Status retrieved successfully",
                    content = @Content(schema =
                    @Schema(implementation = NotificationResponseDTO.class))),
            @ApiResponse(responseCode = "401",
                    description  = "Missing or invalid Bearer token"),
            @ApiResponse(responseCode = "404",
                    description  = "Notification not found")
    })
    @GetMapping("/{id}/status")
    public ResponseEntity<NotificationResponseDTO> getStatus(

            @Parameter(description = "Notification ID", example = "42")
            @PathVariable Long id) {

        log.debug("Status request — notificationId=[{}]", id);
        return ResponseEntity.ok(notificationService.getStatus(id));
    }

    // ── Admin: Manual Status Update ────────────────────────────────

    @Operation(
            summary     = "Manually update notification status (ADMIN only)",
            description = "Allows an admin to override the delivery status " +
                    "of a notification. Useful for ops corrections. " +
                    "Requires ROLE_ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description  = "Status updated successfully",
                    content = @Content(schema =
                    @Schema(implementation = NotificationResponseDTO.class))),
            @ApiResponse(responseCode = "400",
                    description  = "Invalid status value"),
            @ApiResponse(responseCode = "401",
                    description  = "Missing or invalid Bearer token"),
            @ApiResponse(responseCode = "403",
                    description  = "Insufficient permissions — ADMIN role required"),
            @ApiResponse(responseCode = "404",
                    description  = "Notification not found")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationResponseDTO> updateStatus(

            @Parameter(description = "Notification ID", example = "42")
            @PathVariable Long id,

            @Valid @RequestBody StatusUpdateRequestDTO request) {

        log.info("Admin status update — notificationId=[{}] newStatus=[{}]",
                id, request.getStatus());

        return ResponseEntity.ok(
                notificationService.updateStatus(id, request));
    }
}