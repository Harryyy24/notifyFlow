package com.notifyflow.controller;

import com.notifyflow.dto.PreferenceDTO;
import com.notifyflow.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User notification preference endpoints — all require Bearer JWT.
 *
 * GET /api/preferences/{userId}  — retrieve current preferences
 * PUT /api/preferences/{userId}  — create or update preferences
 */
@Slf4j
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
@Tag(name = "Preferences",
        description = "Manage per-user notification channel preferences and quiet hours")
@SecurityRequirement(name = "bearerAuth")
public class PreferenceController {

    private final PreferenceService preferenceService;

    // ── Get Preferences ────────────────────────────────────────────

    @Operation(
            summary     = "Get notification preferences for a user",
            description = "Returns channel toggles and quiet hours settings. " +
                    "Response is cached in Redis for 30 minutes."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description  = "Preferences retrieved successfully",
                    content = @Content(schema =
                    @Schema(implementation = PreferenceDTO.class))),
            @ApiResponse(responseCode = "401",
                    description  = "Missing or invalid Bearer token"),
            @ApiResponse(responseCode = "404",
                    description  = "Preferences not found for this user")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<PreferenceDTO> getPreferences(

            @Parameter(description = "User ID", example = "1")
            @PathVariable Long userId) {

        log.debug("Get preferences — userId=[{}]", userId);
        return ResponseEntity.ok(preferenceService.getPreferences(userId));
    }

    // ── Update Preferences ─────────────────────────────────────────

    @Operation(
            summary     = "Create or update notification preferences",
            description = """
            Sets channel toggles and quiet hours for a user.
            
            - If preferences don't exist yet, they are created.
            - If they already exist, they are updated (upsert).
            - Updating evicts the Redis cache for this user.
            
            Quiet hours example:
              quietHoursStart: "22:00"
              quietHoursEnd:   "08:00"
            
            LOW priority notifications sent during the quiet window
            will receive a 429 response with "Quiet hours active".
            
            Set both fields to null to disable quiet hours.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description  = "Preferences updated successfully",
                    content = @Content(schema =
                    @Schema(implementation = PreferenceDTO.class))),
            @ApiResponse(responseCode = "400",
                    description  = "Validation error"),
            @ApiResponse(responseCode = "401",
                    description  = "Missing or invalid Bearer token"),
            @ApiResponse(responseCode = "404",
                    description  = "User not found")
    })
    @PutMapping("/{userId}")
    public ResponseEntity<PreferenceDTO> updatePreferences(

            @Parameter(description = "User ID", example = "1")
            @PathVariable Long userId,

            @Valid @RequestBody PreferenceDTO request) {

        log.info("Update preferences — userId=[{}]", userId);

        // Ensure the DTO carries the userId from the path
        // (the request body userId field is read-only per Swagger config)
        request.setUserId(userId);

        return ResponseEntity.ok(
                preferenceService.updatePreferences(userId, request));
    }
}