-- ═══════════════════════════════════════════════════════════════
--  V2 — Notifications Table
--  Central audit log for every notification event in the system.
--  Status lifecycle: PENDING → DELIVERED | FAILED
--  kafka_offset stored for debugging / replay traceability.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE notifications (
                               id            BIGINT          NOT NULL AUTO_INCREMENT,
                               user_id       BIGINT          NOT NULL,
                               channel       VARCHAR(20)     NOT NULL,
                               title         VARCHAR(255)    NOT NULL,
                               message       TEXT            NOT NULL,
                               status        VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
                               priority      VARCHAR(10)     NOT NULL DEFAULT 'NORMAL',
                               kafka_offset  BIGINT          NULL      COMMENT 'Kafka partition offset for traceability',
                               retry_count   INT             NOT NULL  DEFAULT 0,
                               created_at    DATETIME(6)     NOT NULL  DEFAULT CURRENT_TIMESTAMP(6),
                               delivered_at  DATETIME(6)     NULL      COMMENT 'Populated when status transitions to DELIVERED',

                               CONSTRAINT pk_notifications           PRIMARY KEY (id),
                               CONSTRAINT fk_notifications_user      FOREIGN KEY (user_id)
                                   REFERENCES  users (id)
                                   ON DELETE CASCADE,
                               CONSTRAINT chk_notifications_channel  CHECK (channel  IN ('EMAIL', 'SMS', 'IN_APP')),
                               CONSTRAINT chk_notifications_status   CHECK (status   IN ('PENDING', 'DELIVERED', 'FAILED')),
                               CONSTRAINT chk_notifications_priority CHECK (priority IN ('HIGH', 'NORMAL', 'LOW'))
);

-- ── Indexes ──────────────────────────────────────────────────────

-- Most common query: "give me all notifications for user X, newest first"
CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

-- Status polling: find all PENDING notifications for retry jobs
CREATE INDEX idx_notifications_status
    ON notifications (status);

-- Channel filtering: analytics queries per channel
CREATE INDEX idx_notifications_channel
    ON notifications (channel);