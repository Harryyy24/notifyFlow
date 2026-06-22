package com.notifyflow.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for JWT generation, parsing, and validation.
 *
 * Uses JJWT 0.12.x API — note the fluent builder style changed
 * significantly from 0.11.x (no more .signWith(key, algorithm),
 * now .signWith(key) with algorithm inferred from key type).
 *
 * Algorithm: HS512 — requires a key of at least 512 bits (64 bytes).
 * The secret in application.yml must be Base64-encoded and >= 64 bytes.
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    // ── Token Generation ───────────────────────────────────────────

    /**
     * Generates a JWT for the given UserDetails.
     * Embeds: subject (email), role claim, issued-at, expiry.
     *
     * @param userDetails the authenticated principal
     * @return signed JWT string
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        // Embed role as a claim so clients can read it without an extra API call
        extraClaims.put("role",
                userDetails.getAuthorities()
                        .iterator().next().getAuthority()
                        .replace("ROLE_", "")          // strip Spring's prefix for clean claim
        );
        return buildToken(extraClaims, userDetails);
    }

    /**
     * Generates a token with custom additional claims.
     * Used in tests and future extension points.
     *
     * @param extraClaims additional claims to embed
     * @param userDetails the authenticated principal
     * @return signed JWT string
     */
    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails) {
        return buildToken(extraClaims, userDetails);
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails) {

        long nowMs = System.currentTimeMillis();

        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())   // username = email in our setup
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + jwtExpirationMs))
                .signWith(getSigningKey())             // JJWT 0.12.x: algorithm inferred
                .compact();
    }

    // ── Token Validation ───────────────────────────────────────────

    /**
     * Validates a token against the given UserDetails.
     * Checks: signature, expiry, subject match.
     *
     * @param token       the JWT to validate
     * @param userDetails the principal to validate against
     * @return true if the token is valid for this user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return email.equals(userDetails.getUsername())
                    && !isTokenExpired(token);
        } catch (Exception ex) {
            log.warn("Token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Checks whether the token's expiry claim is in the past.
     *
     * @param token the JWT to check
     * @return true if expired
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ── Claims Extraction ──────────────────────────────────────────

    /**
     * Extracts the subject (email) from the token.
     *
     * @param token the JWT
     * @return the email embedded as the subject claim
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the expiration date from the token.
     *
     * @param token the JWT
     * @return the expiration Date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts the role claim from the token.
     *
     * @param token the JWT
     * @return role string e.g. "USER" or "ADMIN"
     */
    public String extractRole(String token) {
        return extractClaim(token,
                claims -> claims.get("role", String.class));
    }

    /**
     * Generic claim extractor using a resolver function.
     * All specific extractors delegate to this method.
     *
     * @param token          the JWT
     * @param claimsResolver function to apply to the parsed Claims
     * @param <T>            return type of the resolver
     * @return the resolved claim value
     */
    public <T> T extractClaim(String token,
                              Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // ── Internal helpers ───────────────────────────────────────────

    /**
     * Parses and verifies the token signature, returning all claims.
     * Throws specific JJWT exceptions on failure — caught by the
     * filter and logged as a WARN without leaking stack traces.
     *
     * @param token the JWT
     * @return parsed Claims
     * @throws ExpiredJwtException      if the token has expired
     * @throws MalformedJwtException    if the token structure is invalid
     * @throws SignatureException       if the signature doesn't match
     * @throws UnsupportedJwtException  if the token type isn't supported
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())          // JJWT 0.12.x API
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Derives the HMAC-SHA512 signing key from the configured secret.
     * The secret must be at least 64 bytes for HS512.
     *
     * Keys.hmacShaKeyFor() validates key length and throws
     * WeakKeyException if the secret is too short — fast-fail at startup.
     *
     * @return the SecretKey used to sign and verify tokens
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}