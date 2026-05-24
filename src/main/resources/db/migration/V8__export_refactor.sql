-- Add tenant_id to runs (root of observability hierarchy)
ALTER TABLE runs ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX idx_runs_tenant ON runs(tenant_id);

-- Add tenant_id to metric_snapshots
ALTER TABLE metric_snapshots ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX idx_metric_snapshots_tenant ON metric_snapshots(tenant_id);

-- Per-tenant export destination configuration
CREATE TABLE export_configs (
    config_id       VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    destination_type VARCHAR(16) NOT NULL DEFAULT 'FILE',
    endpoint_url    VARCHAR(512),
    region          VARCHAR(64)  DEFAULT 'us-east-1',
    bucket_name     VARCHAR(256),
    access_key_id   VARCHAR(256),
    secret_access_key VARCHAR(512),
    path_prefix     VARCHAR(512) DEFAULT '',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, destination_type)
);

CREATE INDEX idx_export_configs_tenant ON export_configs(tenant_id);

-- Add retry tracking to export_jobs
ALTER TABLE export_jobs ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE export_jobs ADD COLUMN next_retry_at TIMESTAMPTZ;
ALTER TABLE export_jobs ADD COLUMN parent_job_id VARCHAR(64);

CREATE INDEX idx_export_jobs_parent ON export_jobs(parent_job_id);
CREATE INDEX idx_export_jobs_next_retry ON export_jobs(next_retry_at);
