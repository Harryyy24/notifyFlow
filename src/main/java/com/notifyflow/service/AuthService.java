package com.notifyflow.service;

import com.notifyflow.dto.AuthRequestDTO;
import com.notifyflow.dto.AuthResponseDTO;
import com.notifyflow.dto.RegisterRequestDTO;
import com.notifyflow.exception.DuplicateNotificationException;
import com.notifyflow.model.entity.UserEntity;
import com.notifyflow.model.enums.UserRole;
import com.notifyflow.repository.UserRepository;
import com.notifyflow.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and authentication.
 *
 * Also implements UserDetailsService so Spring Security can load
 * users from MySQL during JWT validation in the filter chain.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtUtil               jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    // ── UserDetailsService ─────────────────────────────────────────

    /**
     * Loads a user by email for Spring Security authentication.
     * Called by the JWT filter on every authenticated request.
     *
     * @param email the email to look up (Spring passes username here)
     * @return UserDetails (our UserEntity implements this directly)
     * @throws UsernameNotFoundException if no user found for the email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));
    }

    // ── Registration ───────────────────────────────────────────────

    /**
     * Registers a new user and returns a JWT so they can
     * authenticate immediately without a second login call.
     *
     * @param request registration details
     * @return AuthResponseDTO with signed JWT
     * @throws IllegalArgumentException if email is already registered
     */
    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        log.info("Registering new user — email=[{}] role=[{}]",
                request.getEmail(), request.getRole());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already registered: " + request.getEmail());
        }

        UserEntity user = UserEntity.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null
                        ? request.getRole() : UserRole.USER)
                .build();

        UserEntity saved = userRepository.save(user);
        log.info("User registered successfully — id=[{}] email=[{}]",
                saved.getId(), saved.getEmail());

        // Generate JWT immediately so client can start making calls
        String token = jwtUtil.generateToken(saved);

        return buildAuthResponse(saved, token);
    }

    // ── Login ──────────────────────────────────────────────────────

    /**
     * Authenticates a user with email + password and returns a JWT.
     *
     * Delegates to Spring's AuthenticationManager which:
     *   1. Calls loadUserByUsername() to get the UserDetails
     *   2. Verifies the password with BCrypt
     *   3. Throws BadCredentialsException if either fails
     *
     * @param request login credentials
     * @return AuthResponseDTO with signed JWT
     */
    @Transactional(readOnly = true)
    public AuthResponseDTO login(AuthRequestDTO request) {
        log.info("Login attempt — email=[{}]", request.getEmail());

        // Let Spring Security do the credential verification
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        UserEntity user = (UserEntity) authentication.getPrincipal();
        String token = jwtUtil.generateToken(user);

        log.info("Login successful — userId=[{}] email=[{}]",
                user.getId(), user.getEmail());

        return buildAuthResponse(user, token);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private AuthResponseDTO buildAuthResponse(UserEntity user, String token) {
        return AuthResponseDTO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}