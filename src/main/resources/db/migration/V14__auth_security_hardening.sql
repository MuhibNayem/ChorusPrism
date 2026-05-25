-- Auth security hardening: refresh tokens, JTI revocation, brute-force, password reset

-- Refresh tokens (rotation strategy: one active refresh token per session)
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 of raw token
    user_id         VARCHAR(64)  NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    tenant_id       VARCHAR(64)  NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    jti             VARCHAR(64)  NOT NULL UNIQUE,  -- links to the access token it was issued with
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user      ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash      ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires   ON refresh_tokens(expires_at);

-- JTI blacklist for access token revocation (logout / password change)
CREATE TABLE revoked_tokens (
    jti         VARCHAR(64)  PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,  -- mirrors access-token expiry for cleanup
    revoked_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens(expires_at);

-- Login attempt tracking for per-email brute-force protection
CREATE TABLE login_attempts (
    id              BIGSERIAL    PRIMARY KEY,
    identifier      VARCHAR(320) NOT NULL,  -- LOWER(email)
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT '',
    attempt_count   INT          NOT NULL DEFAULT 1,
    window_start    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    locked_until    TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_login_attempts_key ON login_attempts(identifier, tenant_id);
CREATE INDEX idx_login_attempts_locked     ON login_attempts(locked_until) WHERE locked_until IS NOT NULL;

-- Password reset tokens (one-time use, 1 hour TTL)
CREATE TABLE password_reset_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     VARCHAR(64)  NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    tenant_id   VARCHAR(64)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_reset_user    ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_expires ON password_reset_tokens(expires_at);

-- Email verification tokens (one-time use, 24 hour TTL)
CREATE TABLE email_verification_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     VARCHAR(64)  NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    tenant_id   VARCHAR(64)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_email_verification_user    ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_expires ON email_verification_tokens(expires_at);

-- last_login_at column (may already exist from application, ensure it's present)
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
