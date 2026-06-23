package com.notifyflow.service;

import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.util.RedisKeyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeduplicationService.
 *
 * RedisTemplate is mocked — no real Redis connection needed.
 * Tests verify the SETNX logic and TTL behaviour in isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeduplicationService Tests")
class DeduplicationServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisKeyUtil redisKeyUtil;

    @InjectMocks
    private DeduplicationService deduplicationService;

    private static final Long              USER_ID  = 1L;
    private static final NotificationChannel CHANNEL = NotificationChannel.EMAIL;
    private static final String            TITLE   = "Test Title";
    private static final String            MESSAGE = "Test Message";
    private static final String            DEDUP_KEY =
            "dedup:1:EMAIL:abc123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                deduplicationService, "dedupTtlMinutes", 10L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisKeyUtil.buildDedupKeyWithTitle(
                USER_ID, CHANNEL, TITLE, MESSAGE))
                .thenReturn(DEDUP_KEY);
    }

    // ── isDuplicate ────────────────────────────────────────────────

    @Test
    @DisplayName("isDuplicate — returns false when key does not exist (new notification)")
    void isDuplicate_returnsFalse_whenKeyNotExists() {
        // SETNX succeeds → key was not present → not a duplicate
        when(valueOperations.setIfAbsent(
                eq(DEDUP_KEY), eq("1"), any(Duration.class)))
                .thenReturn(true);

        boolean result = deduplicationService.isDuplicate(
                USER_ID, CHANNEL, TITLE, MESSAGE);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isDuplicate — returns true when key already exists (duplicate)")
    void isDuplicate_returnsTrue_whenKeyExists() {
        // SETNX fails → key already present → is a duplicate
        when(valueOperations.setIfAbsent(
                eq(DEDUP_KEY), eq("1"), any(Duration.class)))
                .thenReturn(false);

        boolean result = deduplicationService.isDuplicate(
                USER_ID, CHANNEL, TITLE, MESSAGE);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isDuplicate — returns false when Redis returns null (connection failure)")
    void isDuplicate_returnsFalse_whenRedisReturnsNull() {
        // Redis connection failure → null return → treat as non-duplicate
        // Availability over consistency: don't block sends on Redis outage
        when(valueOperations.setIfAbsent(
                eq(DEDUP_KEY), eq("1"), any(Duration.class)))
                .thenReturn(null);

        boolean result = deduplicationService.isDuplicate(
                USER_ID, CHANNEL, TITLE, MESSAGE);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isDuplicate — calls setIfAbsent with correct TTL duration")
    void isDuplicate_callsSetIfAbsent_withCorrectTtl() {
        when(valueOperations.setIfAbsent(
                anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        deduplicationService.isDuplicate(USER_ID, CHANNEL, TITLE, MESSAGE);

        verify(valueOperations).setIfAbsent(
                eq(DEDUP_KEY),
                eq("1"),
                eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("isDuplicate — uses correct Redis key from RedisKeyUtil")
    void isDuplicate_usesCorrectKey() {
        when(valueOperations.setIfAbsent(
                anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        deduplicationService.isDuplicate(USER_ID, CHANNEL, TITLE, MESSAGE);

        verify(redisKeyUtil).buildDedupKeyWithTitle(
                USER_ID, CHANNEL, TITLE, MESSAGE);
        verify(valueOperations).setIfAbsent(
                eq(DEDUP_KEY), anyString(), any(Duration.class));
    }

    // ── removeDedupKey ─────────────────────────────────────────────

    @Test
    @DisplayName("removeDedupKey — calls Redis delete with correct key")
    void removeDedupKey_deletesCorrectKey() {
        when(redisTemplate.delete(DEDUP_KEY)).thenReturn(true);

        deduplicationService.removeDedupKey(
                USER_ID, CHANNEL, TITLE, MESSAGE);

        verify(redisTemplate).delete(DEDUP_KEY);
    }
}