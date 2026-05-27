CREATE TABLE IF NOT EXISTS smtp_configs (
    tenant_id    VARCHAR(64)  PRIMARY KEY,
    host         VARCHAR(255) NOT NULL DEFAULT '',
    port         INTEGER      NOT NULL DEFAULT 587,
    username     VARCHAR(255) NOT NULL DEFAULT '',
    password     TEXT         NOT NULL DEFAULT '',
    from_address VARCHAR(255) NOT NULL DEFAULT 'noreply@chorus.observe',
    use_tls      BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
