package com.notifyflow.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtUtil.
 *
 * No Spring context loaded — pure unit test with ReflectionTestUtils
 * to inject @Value fields that would normally be wired by Spring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtUtil Tests")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // 64-byte Base64-safe secret — meets HS512 minimum key length
    private static final String TEST_SECRET =
            "dGVzdFNlY3JldEtleUZvck5vdGlmeUZsb3dVbml0VGVzdHMx" +
                    "MjM0NTY3ODkwQUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo=";

    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();

        // Inject @Value fields without loading Spring context
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret",     TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", EXPIRATION_MS);

        testUser = new User(
                "test@example.com",
                "hashedPassword",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    // ── Token Generation ───────────────────────────────────────────

    @Test
    @DisplayName("generateToken — returns non-null, non-blank token")
    void generateToken_returnsValidToken() {
        String token = jwtUtil.generateToken(testUser);

        assertThat(token)
                .isNotNull()
                .isNotBlank()
                .contains(".");   // JWT structure: header.payload.signature
    }

    @Test
    @DisplayName("generateToken — token has three parts (header.payload.signature)")
    void generateToken_hasThreeParts() {
        String token = jwtUtil.generateToken(testUser);
        String[] parts = token.split("\\.");

        assertThat(parts).hasSize(3);
    }

    @Test
    @DisplayName("generateToken — different users produce different tokens")
    void generateToken_differentUsersProduceDifferentTokens() {
        UserDetails anotherUser = new User(
                "other@example.com",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        String token1 = jwtUtil.generateToken(testUser);
        String token2 = jwtUtil.generateToken(anotherUser);

        assertThat(token1).isNotEqualTo(token2);
    }

    // ── Claims Extraction ──────────────────────────────────────────

    @Test
    @DisplayName("extractEmail — returns correct email from token")
    void extractEmail_returnsCorrectEmail() {
        String token = jwtUtil.generateToken(testUser);

        String extractedEmail = jwtUtil.extractEmail(token);

        assertThat(extractedEmail).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("extractExpiration — returns future date for valid token")
    void extractExpiration_returnsFutureDate() {
        String token = jwtUtil.generateToken(testUser);

        java.util.Date expiration = jwtUtil.extractExpiration(token);

        assertThat(expiration).isAfter(new java.util.Date());
    }

    @Test
    @DisplayName("extractRole — returns correct role without ROLE_ prefix")
    void extractRole_returnsCleanRole() {
        String token = jwtUtil.generateToken(testUser);

        String role = jwtUtil.extractRole(token);

        assertThat(role).isEqualTo("USER");
    }

    // ── Token Validation ───────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid — returns true for valid token and matching user")
    void isTokenValid_returnsTrueForValidToken() {
        String token = jwtUtil.generateToken(testUser);

        boolean valid = jwtUtil.isTokenValid(token, testUser);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("isTokenValid — returns false when email does not match")
    void isTokenValid_returnsFalseForWrongUser() {
        String token = jwtUtil.generateToken(testUser);

        UserDetails differentUser = new User(
                "hacker@example.com",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        boolean valid = jwtUtil.isTokenValid(token, differentUser);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("isTokenValid — returns false for expired token")
    void isTokenValid_returnsFalseForExpiredToken() {
        // Set expiration to -1ms (already expired)
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", -1L);

        String expiredToken = jwtUtil.generateToken(testUser);

        boolean valid = jwtUtil.isTokenValid(expiredToken, testUser);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired — returns false for fresh token")
    void isTokenExpired_returnsFalseForFreshToken() {
        String token = jwtUtil.generateToken(testUser);

        boolean expired = jwtUtil.isTokenExpired(token);

        assertThat(expired).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired — returns true for expired token")
    void isTokenExpired_returnsTrueForExpiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", -1L);
        String expiredToken = jwtUtil.generateToken(testUser);

        boolean expired = jwtUtil.isTokenExpired(expiredToken);

        assertThat(expired).isTrue();
    }

    @Test
    @DisplayName("extractEmail — throws exception for tampered token")
    void extractEmail_throwsForTamperedToken() {
        String token = jwtUtil.generateToken(testUser);
        // Corrupt the signature part
        String tampered = token.substring(0, token.lastIndexOf('.') + 1)
                + "invalidsignature";

        assertThatThrownBy(() -> jwtUtil.extractEmail(tampered))
                .isInstanceOf(Exception.class);
    }
}