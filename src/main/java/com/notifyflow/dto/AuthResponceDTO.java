package com.notifyflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for both /api/auth/login and /api/auth/register.
 * Contains the signed JWT and basic user info for the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response containing JWT token")
public class AuthResponseDTO {

    @Schema(description = "Signed JWT — include as: Authorization: Bearer <token>",
            example = "eyJhbGciOiJIUzUxMiJ9...")
    private String token;

    @Schema(description = "Token type", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Token expiry in milliseconds", example = "86400000")
    private Long expiresIn;

    @Schema(description = "Authenticated user's ID", example = "1")
    private Long userId;

    @Schema(description = "Authenticated user's email", example = "jane@example.com")
    private String email;

    @Schema(description = "Authenticated user's role", example = "USER")
    private String role;
}