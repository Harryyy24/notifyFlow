package com.notifyflow.service;

import com.notifyflow.dto.PreferenceDTO;
import com.notifyflow.exception.ResourceNotFoundException;
import com.notifyflow.model.entity.UserEntity;
import com.notifyflow.model.entity.UserPreferenceEntity;
import com.notifyflow.repository.PreferenceRepository;
import com.notifyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * Manages user notification preferences.
 *
 * Caching strategy:
 *   GET  → @Cacheable("user-preferences") — cache for 30 minutes
 *   PUT  → @CacheEvict("user-preferences") — evict on update
 *
 * Cache key = userId (simple, stable, unique per user)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;
    private final UserRepository       userRepository;

    // ── Read ───────────────────────────────────────────────────────

    /**
     * Retrieves preferences for a user.
     * Result is cached in Redis under "user-preferences::{userId}".
     *
     * @param userId the user whose preferences to retrieve
     * @return PreferenceDTO with current settings
     * @throws ResourceNotFoundException if user or preferences not found
     */
    @Cacheable(value = "user-preferences", key = "#userId")
    @Transactional(readOnly = true)
    public PreferenceDTO getPreferences(Long userId) {
        log.debug("Loading preferences from DB for userId=[{}]", userId);

        UserPreferenceEntity prefs = preferenceRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Preferences not found for userId: " + userId));

        return mapToDTO(prefs);
    }

    // ── Write ──────────────────────────────────────────────────────

    /**
     * Creates or updates preferences for a user.
     * Evicts the cached entry so next GET loads fresh data.
     *
     * Uses upsert logic — if preferences don't exist yet,
     * creates a new row; otherwise updates the existing one.
     *
     * @param userId the user whose preferences to update
     * @param dto    the new preference values
     * @return updated PreferenceDTO
     * @throws ResourceNotFoundException if the user doesn't exist
     */
    @CacheEvict(value = "user-preferences", key = "#userId")
    @Transactional
    public PreferenceDTO updatePreferences(Long userId, PreferenceDTO dto) {
        log.info("Updating preferences for userId=[{}]", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        UserPreferenceEntity prefs = preferenceRepository
                .findByUserId(userId)
                .orElseGet(() -> {
                    log.debug("No existing preferences for userId=[{}] — creating new",
                            userId);
                    return UserPreferenceEntity.builder()
                            .user(user)
                            .build();
                });

        // Map DTO fields onto entity — only update non-null fields
        if (dto.getEmailEnabled()  != null) prefs.setEmailEnabled(dto.getEmailEnabled());
        if (dto.getSmsEnabled()    != null) prefs.setSmsEnabled(dto.getSmsEnabled());
        if (dto.getInAppEnabled()  != null) prefs.setInAppEnabled(dto.getInAppEnabled());

        // quietHours: set both or neither to keep them consistent
        if (dto.getQuietHoursStart() != null || dto.getQuietHoursEnd() != null) {
            prefs.setQuietHoursStart(dto.getQuietHoursStart());
            prefs.setQuietHoursEnd(dto.getQuietHoursEnd());
        }

        UserPreferenceEntity saved = preferenceRepository.save(prefs);
        log.info("Preferences saved for userId=[{}]", userId);

        return mapToDTO(saved);
    }

    // ── Quiet Hours Check ──────────────────────────────────────────

    /**
     * Checks whether quiet hours are currently active for a user.
     * Delegates to the entity's domain logic for the actual time
     * window calculation (handles overnight windows correctly).
     *
     * @param userId the user to check
     * @return true if quiet hours are active right now
     */
    @Transactional(readOnly = true)
    public boolean isQuietHoursActive(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .map(prefs -> prefs.isQuietHoursActive(LocalTime.now()))
                .orElse(false);  // No preferences = no quiet hours
    }

    /**
     * Checks whether a specific channel is enabled for a user.
     * Uses cached preferences where possible.
     *
     * @param userId  the user to check
     * @param channel the channel to check
     * @return true if the channel is enabled
     */
    @Transactional(readOnly = true)
    public boolean isChannelEnabled(
            Long userId,
            com.notifyflow.model.enums.NotificationChannel channel) {
        return preferenceRepository.findByUserId(userId)
                .map(prefs -> prefs.isChannelEnabled(channel))
                .orElse(true);  // Default to enabled if no prefs exist
    }

    // ── Mapping ────────────────────────────────────────────────────

    private PreferenceDTO mapToDTO(UserPreferenceEntity entity) {
        return PreferenceDTO.builder()
                .userId(entity.getUser().getId())
                .emailEnabled(entity.getEmailEnabled())
                .smsEnabled(entity.getSmsEnabled())
                .inAppEnabled(entity.getInAppEnabled())
                .quietHoursStart(entity.getQuietHoursStart())
                .quietHoursEnd(entity.getQuietHoursEnd())
                .build();
    }
}