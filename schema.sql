-- ============================================
-- PLATFORMS
-- ============================================
CREATE TABLE platforms (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    TEXT NOT NULL UNIQUE,
    api_key                 TEXT NOT NULL UNIQUE,
    session_ttl             INTERVAL NOT NULL DEFAULT '30 minutes',
    max_sessions_per_user   INT,
    totp_available          BOOLEAN NOT NULL DEFAULT false,
    require_device_binding  BOOLEAN NOT NULL DEFAULT false,
    is_active                BOOLEAN NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================
-- USERS
-- ============================================
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_id         UUID NOT NULL REFERENCES platforms(id) ON DELETE CASCADE,
    username            TEXT NOT NULL,
    password_hash       TEXT NOT NULL,
    totp_enabled        BOOLEAN NOT NULL DEFAULT false,
    totp_seed           TEXT,
    totp_backup_codes   TEXT[],
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (platform_id, username)
);

CREATE INDEX idx_users_platform_id ON users(platform_id);

-- ============================================
-- TOTP SESSIONS
-- ============================================
CREATE TABLE totp_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash          TEXT NOT NULL UNIQUE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform_id         UUID NOT NULL REFERENCES platforms(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    consumed_at         TIMESTAMPTZ,
    attempt_count        INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_totp_sessions_token_hash ON totp_sessions(token_hash);
CREATE INDEX idx_totp_sessions_user_id ON totp_sessions(user_id);
CREATE INDEX idx_totp_sessions_expires_at ON totp_sessions(expires_at);

-- ============================================
-- SESSIONS
-- ============================================
CREATE TABLE sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash          TEXT NOT NULL UNIQUE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform_id         UUID NOT NULL REFERENCES platforms(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    last_used_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address          INET,
    user_agent          TEXT
);

CREATE INDEX idx_sessions_token_hash ON sessions(token_hash);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_platform_id ON sessions(platform_id);
CREATE INDEX idx_sessions_expires_at ON sessions(expires_at);