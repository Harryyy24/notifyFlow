package com.notifyflow.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — executes once per request.
 *
 * Filter pipeline:
 * 1. Extract Bearer token from Authorization header
 * 2. Validate token signature + expiry via JwtUtil
 * 3. Load UserDetails from DB
 * 4. Populate SecurityContextHolder so downstream
 *    security checks (@PreAuthorize, etc.) work correctly
 *
 * Extends OncePerRequestFilter to guarantee single execution
 * even if the filter is registered multiple times (Spring quirk).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        final String token = extractToken(request);

        // No token present — continue filter chain unauthenticated
        // Spring Security will reject protected endpoints downstream
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String email = jwtUtil.extractEmail(token);

            // Only authenticate if we have an email AND SecurityContext
            // isn't already populated (e.g. by a previous filter)
            if (email != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                if (jwtUtil.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                          // credentials null post-auth
                                    userDetails.getAuthorities()
                            );

                    // Attach request metadata (IP, session) to the auth token
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    // Populate SecurityContext — makes principal available
                    // to @AuthenticationPrincipal in controllers
                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);

                    log.debug("Authenticated user [{}] for request [{}]",
                            email, request.getRequestURI());
                }
            }
        } catch (Exception ex) {
            // Log but don't rethrow — let the filter chain continue
            // Spring Security will return 401 for the protected resource
            log.warn("JWT authentication failed for request [{}]: {}",
                    request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT from the Authorization header.
     * Returns null if the header is absent or not Bearer-prefixed.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}