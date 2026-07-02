package com.notifyflow.service;

import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Handles notification deduplication using Redis SETNX.
 *
 * Strategy:
 *   On each send request, attempt to SET a key with NX (only if not exists)
 *   and an EX (expiry) of 10 minutes.
 *
 *   - If SET succeeds  → key didn't exist → not a duplicate → allow send
 *   - If SET fails     → key already exists → duplicate within window → reject
 *
 * This is an atomic operation at the Redis level — no race conditions
 * even under high concurrency, unlike a GET-then-SET approach.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisKeyUtil redisKeyUtil;

    @Value("${app.redis.dedup-ttl-minutes}")
    private long dedupTtlMinutes;

    /**
     * Attempts to mark a notification as "in-flight" in Redis.
     *
     * Uses SET NX EX — atomic check-and-set with expiry.
     * If the key already exists (duplicate), returns false.
     * If the key is set successfully (new), returns true.
     *
     * @param userId  the target user's ID
     * @param channel the notification channel
     * @param title   the notification title
     * @param message the notification message
     * @return true if this is NOT a duplicate (safe to send),
     *         false if it IS a duplicate (reject with 409)
     */
    public boolean isDuplicate(Long userId,
                               NotificationChannel channel,
                               String title,
                               String message) {
        String key = redisKeyUtil.buildDedupKeyWithTitle(
                userId, channel, title, message);

        Boolean setResult = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMinutes(dedupTtlMinutes));

        // setIfAbsent returns null if Redis connection fails —
        // treat as non-duplicate to avoid blocking all sends on Redis outage
        boolean isDuplicate = Boolean.FALSE.equals(setResult);

        if (isDuplicate) {
            log.info("Duplicate notification detected — key=[{}] userId=[{}] " +
                    "channel=[{}]", key, userId, channel);
        } else {
            log.debug("Dedup key set — key=[{}] ttl=[{}min]",
                    key, dedupTtlMinutes);
        }

        return isDuplicate;
    }

    /**
     * Manually removes a deduplication key.
     * Used when a notification send fails AFTER the dedup key was set,
     * allowing an immediate retry without waiting for TTL expiry.
     *
     * @param userId  the target user's ID
     * @param channel the notification channel
     * @param title   the notification title
     * @param message the notification message
     */
    public void removeDedupKey(Long userId,
                               NotificationChannel channel,
                               String title,
                               String message) {
        String key = redisKeyUtil.buildDedupKeyWithTitle(
                userId, channel, title, message);
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Dedup key removed — key=[{}] deleted=[{}]", key, deleted);
    }

    /**
     * Returns the remaining TTL of a dedup key in seconds.
     * Used in the 409 Conflict response to tell the client
     * when they can retry.
     *
     * @param userId  the target user's ID
     * @param channel the notification channel
     * @param title   the notification title
     * @param message the notification message
     * @return TTL in seconds, or -2 if key doesn't exist,
     *         or -1 if key has no expiry
     */
    public long getRemainingTtlSeconds(Long userId,
                                       NotificationChannel channel,
                                       String title,
                                       String message) {
        String key = redisKeyUtil.buildDedupKeyWithTitle(
                userId, channel, title, message);
        Long ttl = redisTemplate.getExpire(
                key, java.util.concurrent.TimeUnit.SECONDS);
        return ttl != null ? ttl : -2L;
    }
}