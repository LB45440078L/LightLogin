-- LightLogin portable schema, version 1.
--
-- This single DDL runs unchanged on SQLite, MariaDB/MySQL and PostgreSQL. To achieve that the
-- schema deliberately avoids every construct that differs between them:
--   * primary keys are application-generated VARCHAR(36) UUIDs (no IDENTITY/AUTO_INCREMENT/SERIAL)
--   * timestamps are BIGINT epoch milliseconds in UTC (no native TIMESTAMP)
--   * booleans are INTEGER 0/1 (SQLite has no BOOLEAN)
--   * floats are DOUBLE PRECISION
-- Do not "fix" these back into dialect-specific spellings; that is what makes one file possible.

CREATE TABLE IF NOT EXISTS ll_accounts (
    uuid            VARCHAR(36)  NOT NULL PRIMARY KEY,
    username        VARCHAR(32)  NOT NULL,
    username_lower  VARCHAR(32)  NOT NULL,
    password_hash   VARCHAR(255),
    email           VARCHAR(254),
    status          VARCHAR(16)  NOT NULL,
    failed_attempts INTEGER      NOT NULL,
    locked_until    BIGINT       NOT NULL,
    created_at      BIGINT       NOT NULL,
    last_login      BIGINT       NOT NULL,
    last_ip         VARCHAR(45),
    registration_ip VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_ll_accounts_username_lower ON ll_accounts (username_lower);
CREATE INDEX IF NOT EXISTS idx_ll_accounts_registration_ip ON ll_accounts (registration_ip);

CREATE TABLE IF NOT EXISTS ll_sessions (
    token_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    uuid       VARCHAR(36) NOT NULL,
    ip         VARCHAR(45),
    issued_at  BIGINT      NOT NULL,
    expires_at BIGINT      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ll_sessions_uuid ON ll_sessions (uuid);
CREATE INDEX IF NOT EXISTS idx_ll_sessions_expires ON ll_sessions (expires_at);

CREATE TABLE IF NOT EXISTS ll_ip_bans (
    target     VARCHAR(64) NOT NULL PRIMARY KEY,
    reason     VARCHAR(255),
    actor      VARCHAR(64),
    created_at BIGINT      NOT NULL,
    expires_at BIGINT      NOT NULL,
    source     VARCHAR(16) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ll_ip_bans_expires ON ll_ip_bans (expires_at);

CREATE TABLE IF NOT EXISTS ll_audit (
    id         BIGINT       NOT NULL PRIMARY KEY,
    created_at BIGINT       NOT NULL,
    actor      VARCHAR(64),
    action     VARCHAR(48)  NOT NULL,
    subject    VARCHAR(64),
    detail     VARCHAR(512),
    ip         VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_ll_audit_created ON ll_audit (created_at);
CREATE INDEX IF NOT EXISTS idx_ll_audit_subject ON ll_audit (subject);
CREATE INDEX IF NOT EXISTS idx_ll_audit_action ON ll_audit (action);

CREATE TABLE IF NOT EXISTS ll_admin_accounts (
    username   VARCHAR(64)  NOT NULL PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    role       VARCHAR(16)  NOT NULL,
    created_at BIGINT       NOT NULL,
    last_login BIGINT       NOT NULL,
    totp_secret VARCHAR(64)
);

-- Monotonic counters. The audit id is drawn from here inside the same transaction as the insert,
-- which gives a portable, gap-tolerant, race-free sequence without dialect-specific identity
-- columns.
CREATE TABLE IF NOT EXISTS ll_counters (
    name  VARCHAR(32) NOT NULL PRIMARY KEY,
    value BIGINT      NOT NULL
);