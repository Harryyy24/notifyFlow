-- ═══════════════════════════════════════════════════════════════
--  V1 — Users Table
--  Stores registered application users with BCrypt-hashed passwords.
--  Role column drives Spring Security authorization decisions.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE users (
                       id           BIGINT          NOT NULL AUTO_INCREMENT,
                       name         VARCHAR(100)    NOT NULL,
                       email        VARCHAR(255)    NOT NULL,
                       password_hash VARCHAR(255)   NOT NULL,
                       role         VARCHAR(20)     NOT NULL DEFAULT 'USER',
                       created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                       CONSTRAINT pk_users          PRIMARY KEY (id),
                       CONSTRAINT uq_users_email    UNIQUE      (email),
                       CONSTRAINT chk_users_role    CHECK       (role IN ('USER', 'ADMIN'))
);

-- Index on email — used on every login lookup
CREATE INDEX idx_users_email ON users (email);

-- ── Seed data: one default admin for local dev ──────────────────
-- Password: admin123  (BCrypt hash — change before any real deployment)
INSERT INTO users (name, email, password_hash, role)
VALUES (
           'NotifyFlow Admin',
           'admin@notifyflow.com',
           '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
           'ADMIN'
       );