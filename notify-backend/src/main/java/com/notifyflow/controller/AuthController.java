package com.notifyflow.controller;

import com.notifyflow.dto.AuthRequestDTO;
import com.notifyflow.dto.AuthResponseDTO;
import com.notifyflow.dto.RegisterRequestDTO;
import com.notifyflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints — public (no JWT required).
 *
 * POST /api/auth/register  — create account + receive JWT
 * POST /api/auth/login     — authenticate + receive JWT
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth",
        description = "Register and login to obtain a Bearer JWT token")
public class AuthController {

    private final AuthService authService;

    // ── Register ───────────────────────────────────────────────────

    @Operation(
            summary     = "Register a new user",
            description = "Creates a new account and returns a signed JWT. " +
                    "The token can be used immediately — no separate login required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description  = "User registered successfully",
                    content = @Content(schema =
                    @Schema(implementation = AuthResponseDTO.class))),
            @ApiResponse(responseCode = "400",
                    description  = "Validation error — missing or invalid fields"),
            @ApiResponse(responseCode = "409",
                    description  = "Email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(
            @Valid @RequestBody RegisterRequestDTO request) {

        log.info("Registration request — email=[{}]", request.getEmail());
        AuthResponseDTO response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Admin: Create Admin ────────────────────────────────────────

    @Operation(
            summary     = "Create a new admin (ADMIN only)",
            description = "Allows an existing admin to register a new admin account. " +
                    "Requires ROLE_ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description  = "Admin created successfully",
                    content = @Content(schema =
                    @Schema(implementation = AuthResponseDTO.class))),
            @ApiResponse(responseCode = "400",
                    description  = "Validation error or email already registered"),
            @ApiResponse(responseCode = "403",
                    description  = "Insufficient permissions — ADMIN role required")
    })
    @PostMapping("/admin/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO> registerAdmin(
            @Valid @RequestBody RegisterRequestDTO request) {

        log.info("Admin registration request — email=[{}]", request.getEmail());
        AuthResponseDTO response = authService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Login ──────────────────────────────────────────────────────

    @Operation(
            summary     = "Login with email and password",
            description = "Authenticates credentials and returns a signed JWT. " +
                    "Include the token in all subsequent requests as: " +
                    "Authorization: Bearer <token>"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description  = "Login successful",
                    content = @Content(schema =
                    @Schema(implementation = AuthResponseDTO.class))),
            @ApiResponse(responseCode = "400",
                    description  = "Validation error"),
            @ApiResponse(responseCode = "401",
                    description  = "Invalid email or password")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody AuthRequestDTO request) {

        log.info("Login request — email=[{}]", request.getEmail());
        AuthResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}