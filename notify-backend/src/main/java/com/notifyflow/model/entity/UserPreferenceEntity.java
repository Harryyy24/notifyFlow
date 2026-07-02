package com.notifyflow.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * JPA entity storing per-user notification preferences.
 *
 * One row per user (enforced by UNIQUE constraint on user_id).
 * Quiet hours are stored as {@link LocalTime} — null means
 * no quiet-hours window is configured for that user.
 */
@Entity
@Table(
        name = "user_preferences",
        indexes = {
                @Index(name = "idx_user_preferences_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private Boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    @Builder.Default
    private Boolean smsEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private Boolean inAppEnabled = true;

    /**
     * Start of the quiet hours window (e.g. 22:00).
     * Null means quiet hours are not configured.
     */
    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    /**
     * End of the quiet hours window (e.g. 08:00).
     * Can be less than quietHoursStart for overnight windows (22:00–08:00).
     */
    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    // ── Lifecycle ──────────────────────────────────────────────────

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = java.time.LocalDateTime.now();
    }

    // ── Domain helpers ─────────────────────────────────────────────

    /**
     * Returns true if quiet hours are configured AND the given time
     * falls within the quiet window.
     *
     * Handles overnight windows correctly:
     *   - Normal window  (09:00–17:00): start <= time < end
     *   - Overnight window (22:00–08:00): time >= start OR time < end
     */
    public boolean isQuietHoursActive(LocalTime now) {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }

        if (quietHoursStart.isBefore(quietHoursEnd)) {
            // Same-day window: e.g. 09:00–17:00
            return !now.isBefore(quietHoursStart) && now.isBefore(quietHoursEnd);
        } else {
            // Overnight window: e.g. 22:00–08:00
            return !now.isBefore(quietHoursStart) || now.isBefore(quietHoursEnd);
        }
    }

    /**
     * Returns true if the given channel is enabled for this user.
     */
    public boolean isChannelEnabled(com.notifyflow.model.enums.NotificationChannel channel) {
        return switch (channel) {
            case EMAIL  -> Boolean.TRUE.equals(emailEnabled);
            case SMS    -> Boolean.TRUE.equals(smsEnabled);
            case IN_APP -> Boolean.TRUE.equals(inAppEnabled);
        };
    }
}