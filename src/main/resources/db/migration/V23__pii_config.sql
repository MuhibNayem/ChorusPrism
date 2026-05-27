CREATE TABLE IF NOT EXISTS pii_config (
    tenant_id   VARCHAR(64)  PRIMARY KEY,
    master_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    rules       TEXT         NOT NULL DEFAULT '[]',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
