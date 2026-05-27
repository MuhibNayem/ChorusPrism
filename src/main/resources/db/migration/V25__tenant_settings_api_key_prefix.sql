CREATE TABLE IF NOT EXISTS tenant_settings (
    tenant_id  VARCHAR(64)  NOT NULL,
    key        VARCHAR(128) NOT NULL,
    value      TEXT         NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, key)
);

ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS key_prefix VARCHAR(16);
