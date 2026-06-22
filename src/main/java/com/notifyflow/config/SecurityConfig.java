package com.notifyflow.config;

import com.notifyflow.util.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration.
 *
 * Key design decisions:
 * - Stateless session (JWT-based) — no HttpSession created or used
 * - CSRF disabled — safe for stateless REST APIs (no cookie-based auth)
 * - DaoAuthenticationProvider wires BCrypt + UserDetailsService together
 * - @EnableMethodSecurity enables @PreAuthorize at method level
 *   (used for ADMIN-only endpoints without cluttering the filter chain)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // ── Endpoints that don't require a JWT ────────────────────────
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/actuator/health",
            "/actuator/info"
    };

    /**
     * Main security filter chain.
     *
     * Processing order matters:
     * JwtAuthenticationFilter runs BEFORE UsernamePasswordAuthenticationFilter
     * so the SecurityContext is populated before Spring's own auth checks.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF — not needed for stateless JWT REST APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Disable default form login and HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // Stateless session — Spring Security won't create/use HttpSession
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no JWT required
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // PATCH status endpoint — ADMIN only
                        // (also enforced at method level with @PreAuthorize)
                        .requestMatchers(HttpMethod.PATCH, "/api/notifications/*/status")
                        .hasRole("ADMIN")
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // Register our JWT filter before Spring's auth filter
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }

    /**
     * BCrypt password encoder with strength 12.
     * Strength 12 = ~300ms hash time — strong enough to deter
     * brute force while remaining acceptable for login latency.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Wires UserDetailsService + PasswordEncoder into Spring's
     * DaoAuthenticationProvider — the standard provider for
     * database-backed username/password authentication.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the AuthenticationManager bean so AuthService
     * can call authenticate() directly during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}