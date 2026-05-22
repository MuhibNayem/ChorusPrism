-- ============================================================
-- Enterprise RBAC, Audit, Retention, Export, Notifications, Dashboards
-- ============================================================

-- ============================================================
-- Tenants
-- ============================================================
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id       VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    config          JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    user_id         VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id),
    email           VARCHAR(256) NOT NULL,
    password_hash   VARCHAR(256) NOT NULL,
    display_name    VARCHAR(256),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, email)
);

CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- ============================================================
-- Roles
-- ============================================================
CREATE TABLE IF NOT EXISTS roles (
    role_id         VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id),
    name            VARCHAR(128) NOT NULL,
    permissions     JSONB NOT NULL DEFAULT '[]',
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_roles_tenant ON roles(tenant_id);

-- ============================================================
-- User Roles
-- ============================================================
CREATE TABLE IF NOT EXISTS user_roles (
    user_id         VARCHAR(64) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_id         VARCHAR(64) NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- API Keys (scoped, tenant-aware)
-- ============================================================
CREATE TABLE IF NOT EXISTS api_keys (
    key_hash        VARCHAR(256) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id),
    user_id         VARCHAR(64) REFERENCES users(user_id),
    name            VARCHAR(256) NOT NULL,
    scopes          JSONB NOT NULL DEFAULT '["read"]',
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_api_keys_tenant ON api_keys(tenant_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_user ON api_keys(user_id);

-- ============================================================
-- Audit Logs (append-only, immutable)
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id          VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id),
    user_id         VARCHAR(64),
    action          VARCHAR(64) NOT NULL,       -- CREATE, UPDATE, DELETE, LOGIN, EXPORT, etc.
    resource_type   VARCHAR(64) NOT NULL,       -- run, eval, alert, user, etc.
    resource_id     VARCHAR(64),
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    success         BOOLEAN NOT NULL DEFAULT TRUE,
    details         JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);

-- ============================================================
-- Retention Policies
-- ============================================================
CREATE TABLE IF NOT EXISTS retention_policies (
    policy_id       VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id),
    name            VARCHAR(256) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,       -- runs, spans, eval_results, alert_events, etc.
    retention_days  INT NOT NULL,
    archive_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    archive_location VARCHAR(512),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMPTZ,
    last_run_deleted BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_retention_policies_tenant ON retention_policies(tenant_id);

-- ============================================================
-- Export Jobs
-- ============================================================
CREATE TABLE IF NOT EXISTS export_jobs (
    job_id          VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id),
    user_id         VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,       -- runs, eval_results, etc.
    query_filter    JSONB NOT NULL DEFAULT '{}',
    format          VARCHAR(16) NOT NULL,       -- json, csv, parquet
    destination     VARCHAR(16) NOT NULL,       -- file, s3
    destination_path VARCHAR(512),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    total_records   BIGINT,
    file_size_bytes BIGINT,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_export_jobs_tenant ON export_jobs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_export_jobs_status ON export_jobs(status);

-- ============================================================
-- Notification Channels
-- ============================================================
CREATE TABLE IF NOT EXISTS notification_channels (
    channel_id      VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id),
    name            VARCHAR(256) NOT NULL,
    channel_type    VARCHAR(32) NOT NULL,       -- slack, pagerduty, email, webhook
    config          JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_channels_tenant ON notification_channels(tenant_id);

-- ============================================================
-- Alert Rule Channel Links
-- ============================================================
CREATE TABLE IF NOT EXISTS alert_rule_channels (
    rule_id         VARCHAR(64) NOT NULL REFERENCES alert_rules(rule_id) ON DELETE CASCADE,
    channel_id      VARCHAR(64) NOT NULL REFERENCES notification_channels(channel_id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (rule_id, channel_id)
);

-- ============================================================
-- Dashboards
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboards (
    dashboard_id    VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id),
    user_id         VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    description     TEXT,
    layout          JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dashboards_tenant ON dashboards(tenant_id);

-- ============================================================
-- Dashboard Widgets
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboard_widgets (
    widget_id       VARCHAR(64) PRIMARY KEY,
    dashboard_id    VARCHAR(64) NOT NULL REFERENCES dashboards(dashboard_id) ON DELETE CASCADE,
    widget_type     VARCHAR(32) NOT NULL,       -- line_chart, bar_chart, stat_card, table, pie_chart
    title           VARCHAR(256) NOT NULL,
    query_config    JSONB NOT NULL DEFAULT '{}',
    position        JSONB NOT NULL DEFAULT '{}',
    refresh_seconds INT NOT NULL DEFAULT 60,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dashboard_widgets_dashboard ON dashboard_widgets(dashboard_id);
