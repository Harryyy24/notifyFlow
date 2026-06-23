-- ═══════════════════════════════════════════════════════════════
--  V3 — User Preferences Table
--  Per-user notification channel toggles and quiet hours window.
--  One row per user (enforced by UNIQUE constraint on user_id).
--  quiet_hours_start / quiet_hours_end stored as TIME for
--  easy comparison against CURTIME() in application logic.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE user_preferences (
                                  id                  BIGINT      NOT NULL AUTO_INCREMENT,
                                  user_id             BIGINT      NOT NULL,
                                  email_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
                                  sms_enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
                                  in_app_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
                                  quiet_hours_start   TIME        NULL     COMMENT 'e.g. 22:00:00 — null means no quiet hours',
                                  quiet_hours_end     TIME        NULL     COMMENT 'e.g. 08:00:00',
                                  updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),

                                  CONSTRAINT pk_user_preferences        PRIMARY KEY (id),
                                  CONSTRAINT uq_user_preferences_user   UNIQUE      (user_id),
                                  CONSTRAINT fk_user_preferences_user   FOREIGN KEY (user_id)
                                      REFERENCES  users (id)
                                      ON DELETE CASCADE
);

-- ── Index ─────────────────────────────────────────────────────────
-- Lookup is always by user_id; the UNIQUE constraint above
-- creates an implicit index, but we name it explicitly for clarity.
CREATE INDEX idx_user_preferences_user_id
    ON user_preferences (user_id);

-- ── Seed default preferences for the admin user (id=1) ────────────
INSERT INTO user_preferences (user_id, email_enabled, sms_enabled, in_app_enabled)
VALUES (1, TRUE, TRUE, TRUE);