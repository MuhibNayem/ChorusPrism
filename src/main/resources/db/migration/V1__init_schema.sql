-- Consolidated Database Schema

-- Chorus Observe Phase 1: Persistence Foundation
-- Compatible with PostgreSQL 16+ and TimescaleDB

CREATE TABLE IF NOT EXISTS runs (
    run_id VARCHAR(64) PRIMARY KEY,
    framework VARCHAR(64) NOT NULL,
    agent_id VARCHAR(256) NOT NULL,
    model VARCHAR(128),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    tags JSONB NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}',
    total_tokens INT NOT NULL DEFAULT 0,
    total_cost DECIMAL(18, 8) NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_runs_start_time ON runs(start_time DESC);

CREATE INDEX idx_runs_framework ON runs(framework);

CREATE INDEX idx_runs_agent_id ON runs(agent_id);

CREATE INDEX idx_runs_status ON runs(status);

CREATE INDEX idx_runs_tags ON runs USING GIN(tags);

CREATE TABLE IF NOT EXISTS spans (
    span_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    parent_span_id VARCHAR(64),
    span_name VARCHAR(512) NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    attributes JSONB NOT NULL DEFAULT '{}',
    events JSONB NOT NULL DEFAULT '[]',
    status VARCHAR(16) NOT NULL DEFAULT 'UNSET',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_spans_run_id ON spans(run_id);

CREATE INDEX idx_spans_parent ON spans(parent_span_id);

CREATE INDEX idx_spans_start_time ON spans(start_time DESC);

CREATE TABLE IF NOT EXISTS llm_calls (
    call_id VARCHAR(64) PRIMARY KEY,
    span_id VARCHAR(64) NOT NULL REFERENCES spans(span_id) ON DELETE CASCADE,
    run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    cost_usd DECIMAL(18, 8) NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    prompt TEXT,
    completion TEXT,
    finish_reasons JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_calls_run_id ON llm_calls(run_id);

CREATE INDEX idx_llm_calls_span_id ON llm_calls(span_id);

CREATE INDEX idx_llm_calls_model ON llm_calls(model);

CREATE TABLE IF NOT EXISTS tool_calls (
    call_id VARCHAR(64) PRIMARY KEY,
    span_id VARCHAR(64) NOT NULL REFERENCES spans(span_id) ON DELETE CASCADE,
    run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    tool_name VARCHAR(256) NOT NULL,
    args TEXT,
    result TEXT,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tool_calls_run_id ON tool_calls(run_id);

CREATE INDEX idx_tool_calls_span_id ON tool_calls(span_id);

CREATE INDEX idx_tool_calls_tool_name ON tool_calls(tool_name);

CREATE TABLE IF NOT EXISTS feedback (
    feedback_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    span_id VARCHAR(64) REFERENCES spans(span_id) ON DELETE CASCADE,
    score DECIMAL(4, 2),
    label VARCHAR(128),
    comment TEXT,
    source VARCHAR(64) NOT NULL DEFAULT 'human',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feedback_run_id ON feedback(run_id);

CREATE INDEX idx_feedback_span_id ON feedback(span_id);

CREATE INDEX idx_feedback_source ON feedback(source);

-- TimescaleDB hypertable for metric snapshots (if TimescaleDB is available)
-- Primary key must include timestamp for hypertable compatibility
CREATE TABLE IF NOT EXISTS metric_snapshots (
    snapshot_id VARCHAR(64) NOT NULL,
    metric_name VARCHAR(256) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    tags JSONB NOT NULL DEFAULT '{}',
    timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (snapshot_id, timestamp)
);

CREATE INDEX idx_metric_snapshots_name_time ON metric_snapshots(metric_name, timestamp DESC);

CREATE INDEX idx_metric_snapshots_tags ON metric_snapshots USING GIN(tags);

CREATE TABLE IF NOT EXISTS provenance_entries (
    entry_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    agent_id VARCHAR(256) NOT NULL,
    decision_type VARCHAR(128) NOT NULL,
    input_state TEXT,
    reasoning TEXT,
    output TEXT,
    parent_ids JSONB NOT NULL DEFAULT '[]',
    timestamp TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_provenance_run_id ON provenance_entries(run_id);

CREATE INDEX idx_provenance_timestamp ON provenance_entries(timestamp DESC);

CREATE TABLE IF NOT EXISTS rag_queries (
    query_id VARCHAR(64) PRIMARY KEY,
    span_id VARCHAR(64) NOT NULL REFERENCES spans(span_id) ON DELETE CASCADE,
    run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    query_text TEXT NOT NULL,
    retrieved_chunks TEXT,
    similarity_scores TEXT,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_queries_run_id ON rag_queries(run_id);

CREATE INDEX idx_rag_queries_span_id ON rag_queries(span_id);

-- Convert to hypertable if TimescaleDB extension is present
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('metric_snapshots', 'timestamp', if_not_exists => TRUE);
    END IF;
END $$;

-- Chorus Observe Phases 3-9: Evaluation, Time-Travel, Red Teaming, Monitoring, Prompts
-- Compatible with PostgreSQL 16+ and TimescaleDB

-- ============================================================
-- Phase 3: Evaluation Engine
-- ============================================================

CREATE TABLE IF NOT EXISTS datasets (
    dataset_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    tags JSONB NOT NULL DEFAULT '{}',
    source VARCHAR(64) NOT NULL DEFAULT 'manual',
    split_config JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_datasets_source ON datasets(source);

CREATE INDEX idx_datasets_tags ON datasets USING GIN(tags);

CREATE TABLE IF NOT EXISTS dataset_items (
    item_id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL REFERENCES datasets(dataset_id) ON DELETE CASCADE,
    input TEXT NOT NULL,
    expected_output TEXT,
    metadata JSONB NOT NULL DEFAULT '{}',
    tags JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dataset_items_dataset_id ON dataset_items(dataset_id);

CREATE INDEX idx_dataset_items_tags ON dataset_items USING GIN(tags);

CREATE TABLE IF NOT EXISTS eval_runs (
    eval_run_id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL REFERENCES datasets(dataset_id),
    name VARCHAR(256),
    agent_config JSONB NOT NULL DEFAULT '{}',
    scorer_config JSONB NOT NULL DEFAULT '{}',
    parallelism INT NOT NULL DEFAULT 8,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    summary_metrics JSONB NOT NULL DEFAULT '{}',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_eval_runs_dataset_id ON eval_runs(dataset_id);

CREATE INDEX idx_eval_runs_status ON eval_runs(status);

CREATE INDEX idx_eval_runs_created_at ON eval_runs(created_at DESC);

CREATE TABLE IF NOT EXISTS eval_results (
    result_id VARCHAR(64) PRIMARY KEY,
    eval_run_id VARCHAR(64) NOT NULL REFERENCES eval_runs(eval_run_id) ON DELETE CASCADE,
    item_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) REFERENCES runs(run_id),
    span_id VARCHAR(64) REFERENCES spans(span_id),
    actual_output TEXT,
    score DOUBLE PRECISION NOT NULL DEFAULT 0,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    reasoning TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_eval_results_eval_run_id ON eval_results(eval_run_id);

CREATE INDEX idx_eval_results_run_id ON eval_results(run_id);

CREATE INDEX idx_eval_results_passed ON eval_results(passed);

-- ============================================================
-- Phase 4: Time-Travel Debugger
-- ============================================================

CREATE TABLE IF NOT EXISTS checkpoints (
    checkpoint_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    sequence INT NOT NULL,
    state_snapshot JSONB NOT NULL DEFAULT '{}',
    next_nodes JSONB NOT NULL DEFAULT '[]',
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, sequence)
);

CREATE INDEX idx_checkpoints_run_id ON checkpoints(run_id);

CREATE INDEX idx_checkpoints_run_seq ON checkpoints(run_id, sequence);

CREATE TABLE IF NOT EXISTS replay_runs (
    replay_run_id VARCHAR(64) PRIMARY KEY,
    original_run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id),
    from_checkpoint_id VARCHAR(64) REFERENCES checkpoints(checkpoint_id),
    state_overrides JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_replay_runs_original ON replay_runs(original_run_id);

CREATE TABLE IF NOT EXISTS breakpoints (
    breakpoint_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    before_node VARCHAR(256),
    before_tool VARCHAR(256),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_breakpoints_run_id ON breakpoints(run_id);

CREATE INDEX idx_breakpoints_status ON breakpoints(status);

-- ============================================================
-- Phase 5: Red Teaming + Safety Studio
-- ============================================================

CREATE TABLE IF NOT EXISTS red_team_scenarios (
    scenario_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    category VARCHAR(128) NOT NULL,
    attack_prompt TEXT NOT NULL,
    expected_behavior TEXT,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_red_team_category ON red_team_scenarios(category);

CREATE INDEX idx_red_team_severity ON red_team_scenarios(severity);

CREATE TABLE IF NOT EXISTS red_team_runs (
    red_team_run_id VARCHAR(64) PRIMARY KEY,
    agent_config JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    total_scenarios INT NOT NULL DEFAULT 0,
    bypassed_count INT NOT NULL DEFAULT 0,
    blocked_count INT NOT NULL DEFAULT 0,
    summary_metrics JSONB NOT NULL DEFAULT '{}',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_red_team_runs_status ON red_team_runs(status);

CREATE TABLE IF NOT EXISTS red_team_results (
    result_id VARCHAR(64) PRIMARY KEY,
    red_team_run_id VARCHAR(64) NOT NULL REFERENCES red_team_runs(red_team_run_id) ON DELETE CASCADE,
    scenario_id VARCHAR(64) NOT NULL REFERENCES red_team_scenarios(scenario_id),
    agent_output TEXT,
    guardrail_result JSONB NOT NULL DEFAULT '{}',
    bypassed BOOLEAN NOT NULL DEFAULT FALSE,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_red_team_results_run_id ON red_team_results(red_team_run_id);

CREATE INDEX idx_red_team_results_bypassed ON red_team_results(bypassed);

CREATE TABLE IF NOT EXISTS guardrail_telemetry (
    telemetry_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) REFERENCES runs(run_id),
    guardrail_name VARCHAR(256) NOT NULL,
    tier INT NOT NULL DEFAULT 1,
    action VARCHAR(16) NOT NULL,
    confidence DOUBLE PRECISION,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_guardrail_telemetry_run_id ON guardrail_telemetry(run_id);

CREATE INDEX idx_guardrail_telemetry_name ON guardrail_telemetry(guardrail_name);

CREATE INDEX idx_guardrail_telemetry_created ON guardrail_telemetry(created_at DESC);

-- ============================================================
-- Phase 6: Production Monitoring + Alert Engine
-- ============================================================

CREATE TABLE IF NOT EXISTS alert_rules (
    rule_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    condition_expr TEXT NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    webhook_url VARCHAR(512),
    email VARCHAR(256),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    cooldown_seconds INT NOT NULL DEFAULT 300,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_rules_enabled ON alert_rules(enabled);

CREATE TABLE IF NOT EXISTS alert_events (
    event_id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL REFERENCES alert_rules(rule_id) ON DELETE CASCADE,
    triggered_at TIMESTAMPTZ NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    resolved_at TIMESTAMPTZ,
    notification_sent BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_alert_events_rule_id ON alert_events(rule_id);

CREATE INDEX idx_alert_events_triggered ON alert_events(triggered_at DESC);

CREATE TABLE IF NOT EXISTS trace_clusters (
    cluster_id VARCHAR(64) PRIMARY KEY,
    label VARCHAR(256) NOT NULL,
    description TEXT,
    run_count INT NOT NULL DEFAULT 0,
    avg_score DOUBLE PRECISION,
    avg_cost DOUBLE PRECISION,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trace_clusters_period ON trace_clusters(period_start, period_end);

CREATE TABLE IF NOT EXISTS budget_enforcements (
    enforcement_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(256) NOT NULL,
    budget_type VARCHAR(64) NOT NULL DEFAULT 'per_run',
    limit_value DECIMAL(18, 8) NOT NULL,
    current_value DECIMAL(18, 8) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_budget_enforcements_agent ON budget_enforcements(agent_id);

CREATE INDEX idx_budget_enforcements_status ON budget_enforcements(status);

-- ============================================================
-- Phase 7: Prompt Management + Playground
-- ============================================================

CREATE TABLE IF NOT EXISTS prompt_versions (
    version_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    model VARCHAR(128),
    temperature DOUBLE PRECISION,
    max_tokens INT,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_by VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prompt_versions_name ON prompt_versions(name);

CREATE INDEX idx_prompt_versions_created ON prompt_versions(created_at DESC);

CREATE TABLE IF NOT EXISTS prompt_tags (
    version_id VARCHAR(64) NOT NULL REFERENCES prompt_versions(version_id) ON DELETE CASCADE,
    tag_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (version_id, tag_name)
);

CREATE INDEX idx_prompt_tags_name ON prompt_tags(tag_name);

CREATE TABLE IF NOT EXISTS prompt_ab_tests (
    test_id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) REFERENCES datasets(dataset_id),
    prompt_a_id VARCHAR(64) NOT NULL REFERENCES prompt_versions(version_id),
    prompt_b_id VARCHAR(64) NOT NULL REFERENCES prompt_versions(version_id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    winner_id VARCHAR(64) REFERENCES prompt_versions(version_id),
    p_value DOUBLE PRECISION,
    summary_metrics JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_prompt_ab_tests_status ON prompt_ab_tests(status);

-- ============================================================
-- Phase 8: SQL Query Engine (no new tables — uses existing schema)
-- Phase 9: Universal Adapter (no new tables — A2A agent card is computed)
-- ============================================================

-- Enterprise-grade features: distributed locking, trace embeddings, multi-turn testing

-- ============================================================
-- Distributed Locking
-- ============================================================
CREATE TABLE IF NOT EXISTS distributed_locks (
    lock_name       VARCHAR(256) PRIMARY KEY,
    owner_id        VARCHAR(256) NOT NULL,
    acquired_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_distributed_locks_expires ON distributed_locks(expires_at);

-- ============================================================
-- Trace Embeddings (for cluster analysis)
-- ============================================================
CREATE TABLE IF NOT EXISTS trace_embeddings (
    embedding_id    VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    span_id         VARCHAR(64) REFERENCES spans(span_id) ON DELETE CASCADE,
    model           VARCHAR(128) NOT NULL,
    vector          JSONB NOT NULL,           -- array of floats stored as JSON
    text_source     TEXT NOT NULL,            -- the text that was embedded
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trace_embeddings_run ON trace_embeddings(run_id);

CREATE INDEX IF NOT EXISTS idx_trace_embeddings_model ON trace_embeddings(model);

-- ============================================================
-- Multi-Turn Testing
-- ============================================================
CREATE TABLE IF NOT EXISTS multi_turn_scenarios (
    scenario_id     VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    description     TEXT,
    turns           JSONB NOT NULL,           -- array of {role, message, expected_keywords}
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS multi_turn_runs (
    run_id          VARCHAR(64) PRIMARY KEY,
    scenario_id     VARCHAR(64) NOT NULL REFERENCES multi_turn_scenarios(scenario_id),
    agent_config    JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    total_turns     INT NOT NULL DEFAULT 0,
    passed_turns    INT NOT NULL DEFAULT 0,
    failed_turns    INT NOT NULL DEFAULT 0,
    final_score     DOUBLE PRECISION,
    summary_metrics JSONB NOT NULL DEFAULT '{}',
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_multi_turn_runs_scenario ON multi_turn_runs(scenario_id);

CREATE INDEX IF NOT EXISTS idx_multi_turn_runs_status ON multi_turn_runs(status);

CREATE TABLE IF NOT EXISTS multi_turn_turns (
    turn_id         VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES multi_turn_runs(run_id) ON DELETE CASCADE,
    turn_index      INT NOT NULL,
    role            VARCHAR(32) NOT NULL,
    input_message   TEXT NOT NULL,
    agent_output    TEXT,
    expected_keywords JSONB NOT NULL DEFAULT '[]',
    matched_keywords JSONB NOT NULL DEFAULT '[]',
    score           DOUBLE PRECISION,
    passed          BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms      BIGINT NOT NULL DEFAULT 0,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_multi_turn_turns_run ON multi_turn_turns(run_id);

-- ============================================================
-- Budget Enforcement Events Log
-- ============================================================
CREATE TABLE IF NOT EXISTS budget_events (
    event_id        VARCHAR(64) PRIMARY KEY,
    enforcement_id  VARCHAR(64) NOT NULL REFERENCES budget_enforcements(enforcement_id),
    event_type      VARCHAR(32) NOT NULL,     -- SPEND, THRESHOLD, EXCEEDED, BLOCKED
    amount          DECIMAL(18, 8) NOT NULL DEFAULT 0,
    currency        VARCHAR(8) NOT NULL DEFAULT 'USD',
    description     TEXT,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_budget_events_enforcement ON budget_events(enforcement_id);

CREATE INDEX IF NOT EXISTS idx_budget_events_type ON budget_events(event_type);

-- Add token_id to distributed_locks for proper ownership validation
ALTER TABLE distributed_locks ADD COLUMN IF NOT EXISTS token_id VARCHAR(64);

-- Create index for token lookups
CREATE INDEX IF NOT EXISTS idx_distributed_locks_token ON distributed_locks(token_id);

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

-- ============================================================
-- Agents, Models, Evaluators, Span Types, LLM Messages
-- ============================================================

-- ============================================================
-- Agents
-- ============================================================
CREATE TABLE IF NOT EXISTS agents (
    agent_id        VARCHAR(256) PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    description     TEXT,
    framework       VARCHAR(64),
    runtime         VARCHAR(128),
    owner           VARCHAR(128),
    owner_email     VARCHAR(256),
    tags            JSONB NOT NULL DEFAULT '[]',
    version         VARCHAR(32),
    deployed_at     TIMESTAMPTZ,
    deployed_by     VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'healthy',
    health          DECIMAL(5,2),
    repo            VARCHAR(512),
    branch          VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agents_status ON agents(status);

CREATE INDEX IF NOT EXISTS idx_agents_framework ON agents(framework);

-- ============================================================
-- Evaluators
-- ============================================================
CREATE TABLE IF NOT EXISTS evaluators (
    evaluator_id    VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    kind            VARCHAR(32) NOT NULL,
    description     TEXT,
    config          JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evaluators_kind ON evaluators(kind);

-- ============================================================
-- Run Evaluations
-- ============================================================
CREATE TABLE IF NOT EXISTS run_evaluations (
    evaluation_id   VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    evaluator_id    VARCHAR(64) NOT NULL REFERENCES evaluators(evaluator_id),
    score           DECIMAL(4,3) NOT NULL,
    passed          BOOLEAN NOT NULL DEFAULT FALSE,
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_run_evaluations_run ON run_evaluations(run_id);

CREATE INDEX IF NOT EXISTS idx_run_evaluations_evaluator ON run_evaluations(evaluator_id);

-- ============================================================
-- Span enhancements
-- ============================================================
ALTER TABLE spans ADD COLUMN IF NOT EXISTS span_type VARCHAR(16);

ALTER TABLE spans ADD COLUMN IF NOT EXISTS first_token_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_spans_type ON spans(span_type);

-- ============================================================
-- LLM call enhancements
-- ============================================================
ALTER TABLE llm_calls ADD COLUMN IF NOT EXISTS messages JSONB;

-- OAuth2/OIDC IdP configurations per tenant (multiple IdPs supported)
CREATE TABLE tenant_oauth_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    provider_name   VARCHAR(64) NOT NULL,
    client_id       VARCHAR(256) NOT NULL,
    client_secret   VARCHAR(512) NOT NULL,
    issuer_uri      VARCHAR(512) NOT NULL,
    scopes          JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_role    VARCHAR(64) NOT NULL DEFAULT 'VIEWER',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, provider_name)
);

CREATE INDEX idx_tenant_oauth_configs_tenant ON tenant_oauth_configs(tenant_id);

-- SAML IdP configurations per tenant (multiple IdPs supported)
CREATE TABLE tenant_saml_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    provider_name   VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(512) NOT NULL,
    sign_on_url     VARCHAR(512) NOT NULL,
    signing_cert_thumbprint VARCHAR(128) NOT NULL,
    metadata_url    VARCHAR(512),
    acs_url         VARCHAR(512) NOT NULL,
    default_role    VARCHAR(64) NOT NULL DEFAULT 'VIEWER',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, provider_name)
);

CREATE INDEX idx_tenant_saml_configs_tenant ON tenant_saml_configs(tenant_id);

-- SCIM provisioning tokens (separate from api_keys)
CREATE TABLE scim_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    token_hash      VARCHAR(256) NOT NULL UNIQUE,
    scopes          JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_scim_tokens_tenant ON scim_tokens(tenant_id);

CREATE INDEX idx_scim_tokens_hash ON scim_tokens(token_hash);

-- Add auth_source to users for tracking login method
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_source VARCHAR(16) NOT NULL DEFAULT 'LOCAL';

-- LOWER(email) unique constraint per tenant for duplicate prevention (SCIM-07)
CREATE UNIQUE INDEX idx_users_tenant_lower_email ON users(tenant_id, LOWER(email));

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

-- Alert delivery retry tracking
ALTER TABLE alert_events ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE alert_events ADD COLUMN next_retry_at TIMESTAMPTZ;

ALTER TABLE alert_events ADD COLUMN last_error TEXT;

CREATE INDEX idx_alert_events_retry ON alert_events(next_retry_at) WHERE retry_count < 3;

-- Generated eval cases from production traces
CREATE TABLE generated_eval_cases (
    case_id         VARCHAR(64) PRIMARY KEY,
    source_run_id   VARCHAR(64) NOT NULL REFERENCES runs(run_id),
    source_span_id  VARCHAR(64),
    input           TEXT NOT NULL,
    expected_output TEXT,
    metadata        JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
    reviewed_by     VARCHAR(64),
    reviewed_at     TIMESTAMPTZ,
    review_notes    TEXT,
    dataset_id      VARCHAR(64) REFERENCES datasets(dataset_id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_generated_eval_cases_status ON generated_eval_cases(status);

CREATE INDEX idx_generated_eval_cases_source_run ON generated_eval_cases(source_run_id);

-- N-run scoring individual results
CREATE TABLE eval_result_runs (
    result_run_id   VARCHAR(64) PRIMARY KEY,
    result_id       VARCHAR(64) NOT NULL REFERENCES eval_results(result_id) ON DELETE CASCADE,
    run_number      INT NOT NULL,
    score           DOUBLE PRECISION NOT NULL DEFAULT 0,
    passed          BOOLEAN NOT NULL DEFAULT FALSE,
    actual_output   TEXT,
    reasoning       TEXT,
    latency_ms      BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (result_id, run_number)
);

CREATE INDEX idx_eval_result_runs_result_id ON eval_result_runs(result_id);

-- Add min_runs column to eval_runs for N-run scoring configuration
ALTER TABLE eval_runs ADD COLUMN min_runs INT NOT NULL DEFAULT 1;

-- Wave 1 Security Hardening: Add tenant_id to tables missing tenant isolation
-- All existing rows are assigned to the 'default' tenant.

-- ============================================================
-- Phase 2 tables (Evaluation, Datasets)
-- ============================================================
ALTER TABLE datasets ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_datasets_tenant ON datasets(tenant_id);

ALTER TABLE dataset_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_dataset_items_tenant ON dataset_items(tenant_id);

ALTER TABLE eval_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_eval_runs_tenant ON eval_runs(tenant_id);

ALTER TABLE eval_results ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_eval_results_tenant ON eval_results(tenant_id);

-- ============================================================
-- Phase 3 tables (Time-Travel)
-- ============================================================
ALTER TABLE checkpoints ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_checkpoints_tenant ON checkpoints(tenant_id);

ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_replay_runs_tenant ON replay_runs(tenant_id);

ALTER TABLE breakpoints ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_breakpoints_tenant ON breakpoints(tenant_id);

-- ============================================================
-- Phase 4 tables (Red Teaming)
-- ============================================================
ALTER TABLE red_team_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_red_team_runs_tenant ON red_team_runs(tenant_id);

ALTER TABLE red_team_results ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_red_team_results_tenant ON red_team_results(tenant_id);

-- red_team_scenarios are shared templates — no tenant isolation needed

-- ============================================================
-- Phase 5 tables (Monitoring, Alerts, Budgets)
-- ============================================================
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_alert_rules_tenant ON alert_rules(tenant_id);

ALTER TABLE alert_events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_alert_events_tenant ON alert_events(tenant_id);

ALTER TABLE trace_clusters ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_trace_clusters_tenant ON trace_clusters(tenant_id);

ALTER TABLE budget_enforcements ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_budget_enforcements_tenant ON budget_enforcements(tenant_id);

ALTER TABLE budget_events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_budget_events_tenant ON budget_events(tenant_id);

ALTER TABLE guardrail_telemetry ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_guardrail_telemetry_tenant ON guardrail_telemetry(tenant_id);

-- ============================================================
-- Phase 6 tables (Agents, Models, Evaluators)
-- ============================================================
ALTER TABLE agents ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_agents_tenant ON agents(tenant_id);

-- evaluators are shared templates — no tenant isolation needed

-- ============================================================
-- Phase 7 tables (Prompts)
-- ============================================================
ALTER TABLE prompt_versions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_prompt_versions_tenant ON prompt_versions(tenant_id);

ALTER TABLE prompt_ab_tests ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_prompt_ab_tests_tenant ON prompt_ab_tests(tenant_id);

-- prompt_tags inherits tenant via prompt_versions — no direct tenant_id needed

-- ============================================================
-- Phase 8 tables (Multi-Turn)
-- ============================================================
ALTER TABLE multi_turn_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_multi_turn_runs_tenant ON multi_turn_runs(tenant_id);

ALTER TABLE multi_turn_turns ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_multi_turn_turns_tenant ON multi_turn_turns(tenant_id);

-- multi_turn_scenarios are shared templates — no tenant isolation needed

-- ============================================================
-- Phase 9 tables (Trace Embeddings, Provenance, RAG)
-- ============================================================
ALTER TABLE trace_embeddings ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_trace_embeddings_tenant ON trace_embeddings(tenant_id);

ALTER TABLE provenance_entries ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_provenance_entries_tenant ON provenance_entries(tenant_id);

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_rag_queries_tenant ON rag_queries(tenant_id);

-- ============================================================
-- Phase 10 tables (Generated Eval Cases, Run Evaluations)
-- ============================================================
ALTER TABLE generated_eval_cases ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_generated_eval_cases_tenant ON generated_eval_cases(tenant_id);

ALTER TABLE run_evaluations ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_run_evaluations_tenant ON run_evaluations(tenant_id);

ALTER TABLE eval_result_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_eval_result_runs_tenant ON eval_result_runs(tenant_id);

-- ============================================================
-- Child tables of runs (spans, llm_calls, tool_calls, feedback)
-- Tenant isolation is enforced via JOIN with runs.tenant_id
-- No direct tenant_id column needed — keeps FK cascade semantics clean
-- ============================================================

-- ============================================================
-- Add composite indexes for common tenant-scoped queries
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_datasets_tenant_created ON datasets(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_eval_runs_tenant_status ON eval_runs(tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_alert_rules_tenant_enabled ON alert_rules(tenant_id, enabled);

CREATE INDEX IF NOT EXISTS idx_prompt_versions_tenant_created ON prompt_versions(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agents_tenant_status ON agents(tenant_id, status);

-- Resilient dynamic pgvector database integration
-- Installs the pgvector extension and adds a native vector column for trace clustering

DO $$
BEGIN
    -- Check if vector extension is available in pg_available_extensions
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        -- Safely install the extension
        CREATE EXTENSION IF NOT EXISTS vector;
    END IF;
END $$;

DO $$
BEGIN
    -- Check if extension is installed successfully
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- Add vector_native column of dynamic-dimension type 'vector'
        ALTER TABLE trace_embeddings ADD COLUMN IF NOT EXISTS vector_native vector;

        -- Create HNSW index for ultra-fast cosine similarity search (using dynamic dimension index if supported)
        -- Fallback to standard index mapping if HNSW requires a dimension constraint
        CREATE INDEX IF NOT EXISTS idx_trace_embeddings_vector_native_cosine ON trace_embeddings USING hnsw (vector_native vector_cosine_ops);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        -- Log warning and complete successfully to prevent startup blocker under strict DB restrictions
        RAISE WARNING 'Failed to initialize native pgvector features. Falling back to JSONB storage. Error: %', SQLERRM;
END $$;

-- Transactional outbox queue for resilient trace ingestion
-- Serializes and buffers raw incoming traces dynamically, guaranteeing zero data loss

CREATE TABLE IF NOT EXISTS ingestion_queue (
    queue_id        VARCHAR(64) PRIMARY KEY,
    trace_id        VARCHAR(64) NOT NULL,
    span_id         VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ,
    kind            INT NOT NULL,
    status_code     INT NOT NULL,
    attributes      JSONB NOT NULL,
    events          JSONB NOT NULL DEFAULT '[]',
    parent_span_id  VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ingestion_queue_created ON ingestion_queue(created_at);

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

-- Migration to add the missing created_at column to the alert_events table
ALTER TABLE alert_events ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- ============================================================
-- Continuous Evaluation Loops Persistence
-- ============================================================
CREATE TABLE IF NOT EXISTS eval_loops (
    loop_id           VARCHAR(64) PRIMARY KEY,
    agent_id          VARCHAR(256) NOT NULL REFERENCES agents(agent_id) ON DELETE CASCADE,
    evaluator_id      VARCHAR(64) NOT NULL REFERENCES evaluators(evaluator_id) ON DELETE CASCADE,
    sampling_rate     INT NOT NULL DEFAULT 100,
    alert_threshold   DECIMAL(4,3) NOT NULL DEFAULT 0.850,
    status            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_run_at       TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_eval_loops_agent ON eval_loops(agent_id);

-- ============================================================
-- V19: World-class RAG Analytics
--   • Fixes tenant_id gap in rag_queries
--   • Adds RAGAS-style scoring columns
--   • Adds rag_drift_snapshots for embedding drift detection
--   • Seeds 250 realistic RAG queries across 5 collections / 30 days
-- ============================================================

-- ── 1. Tenant isolation fix ───────────────────────────────────────────────────

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_rag_queries_tenant      ON rag_queries(tenant_id);

CREATE INDEX IF NOT EXISTS idx_rag_queries_tenant_time ON rag_queries(tenant_id, created_at DESC);

-- ── 2. RAGAS scoring columns ──────────────────────────────────────────────────

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS context_precision  FLOAT;

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS context_recall     FLOAT;

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS faithfulness       FLOAT;

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS answer_relevancy   FLOAT;

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS chunk_count        INT  NOT NULL DEFAULT 0;

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS collection         VARCHAR(256);

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS top_k              INT  NOT NULL DEFAULT 5;

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS query_embedding    JSONB;

CREATE INDEX IF NOT EXISTS idx_rag_queries_collection  ON rag_queries(collection) WHERE collection IS NOT NULL;

-- ── 3. Embedding drift snapshots ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS rag_drift_snapshots (
    snapshot_id      VARCHAR(64)  PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    collection       VARCHAR(256),
    period_start     TIMESTAMPTZ  NOT NULL,
    period_end       TIMESTAMPTZ  NOT NULL,
    mean_cosine_shift  FLOAT      NOT NULL DEFAULT 0.0,
    query_volume_delta FLOAT      NOT NULL DEFAULT 0.0,
    precision_delta    FLOAT      NOT NULL DEFAULT 0.0,
    alert_level      VARCHAR(16)  NOT NULL DEFAULT 'none',
    metadata         JSONB        NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rag_drift_tenant_time ON rag_drift_snapshots(tenant_id, created_at DESC);

-- ── 4. Seed data ──────────────────────────────────────────────────────────────

DO $$
DECLARE
    collections   TEXT[] := ARRAY['product_docs','support_kb','technical_specs','billing_docs','api_reference'];
    query_texts   TEXT[] := ARRAY[
        'How do I reset my password?',
        'What is the API rate limit for free tier?',
        'Explain the enterprise pricing model',
        'How to integrate with Slack webhooks?',
        'Troubleshoot 503 service unavailable errors',
        'What are the data retention policies?',
        'How to export my data to CSV?',
        'What SLA guarantees are provided?',
        'How does SSO login work?',
        'Describe the billing cycle and invoicing',
        'How to set up SCIM provisioning?',
        'What encryption standards are used?',
        'How to configure custom domains?',
        'What are the token usage limits?',
        'How to create an API key?',
        'Explain the retry logic for failed requests',
        'How to enable MFA for my account?',
        'What is the maximum file upload size?',
        'How to migrate from v1 to v2 API?',
        'Describe the audit log format',
        'How to set up cost alerts?',
        'What regions are supported for deployment?',
        'How to clone a workspace?',
        'Explain semantic search vs keyword search',
        'How to use the batch processing endpoint?',
        'What are the compliance certifications?',
        'How to handle webhook signature verification?',
        'What is the context window limit?',
        'How to enable verbose logging?',
        'Describe the agent memory architecture'
    ];
    agents_rag   TEXT[] := ARRAY['agent_support_bot','agent_support_bot','agent_support_bot',
                                  'agent_code_reviewer','agent_data_pipeline'];
    frameworks   TEXT[] := ARRAY['langchain','langchain','langchain','openai-sdk','crewai'];
    i            INT;
    q_idx        INT;
    col_idx      INT;
    agent_idx    INT;
    hour_offset  FLOAT;
    start_ts     TIMESTAMPTZ;
    lat_ms       BIGINT;
    prec         FLOAT;
    rec          FLOAT;
    faith        FLOAT;
    rel          FLOAT;
    chunk_cnt    INT;
    top_k_val    INT;
    r_id         TEXT;
    sp_id        TEXT;
    qid          TEXT;
    scores_arr   TEXT;
    chunks_arr   TEXT;
    hit_cache    BOOLEAN;
BEGIN
    FOR i IN 1..250 LOOP
        q_idx      := (i % array_length(query_texts, 1)) + 1;
        col_idx    := (i % 5) + 1;
        agent_idx  := (i % 5) + 1;
        hour_offset := random() * 720;
        start_ts   := NOW() - (hour_offset || ' hours')::INTERVAL;

        -- Realistic RAG latency: bimodal — cache hits fast, misses slower
        hit_cache  := random() < 0.38;
        lat_ms     := CASE WHEN hit_cache THEN (20 + random() * 80)::BIGINT
                           ELSE (80 + random() * 420)::BIGINT END;

        chunk_cnt  := (3 + random() * 7)::INT;
        top_k_val  := chunk_cnt + (random() * 2)::INT;

        -- Similarity scores array as text "[s1, s2, ...]"
        scores_arr := '[';
        FOR j IN 1..chunk_cnt LOOP
            IF j > 1 THEN scores_arr := scores_arr || ', '; END IF;
            scores_arr := scores_arr || ROUND((0.65 + random() * 0.32)::NUMERIC, 4)::TEXT;
        END LOOP;
        scores_arr := scores_arr || ']';

        -- Context precision: avg of scores (0.72–0.96 range for good RAG)
        prec  := ROUND((0.72 + random() * 0.24)::NUMERIC, 4);
        -- Context recall: fraction of needed context retrieved
        rec   := ROUND((0.64 + random() * 0.28)::NUMERIC, 4);
        -- Faithfulness: is answer grounded in context?
        faith := ROUND((0.70 + random() * 0.27)::NUMERIC, 4);
        -- Answer relevancy: semantic closeness of answer to query
        rel   := ROUND((0.75 + random() * 0.22)::NUMERIC, 4);

        -- Occasional low-quality retrieval (8% of queries)
        IF random() < 0.08 THEN
            prec  := ROUND((0.40 + random() * 0.25)::NUMERIC, 4);
            rec   := ROUND((0.35 + random() * 0.25)::NUMERIC, 4);
            faith := ROUND((0.42 + random() * 0.25)::NUMERIC, 4);
        END IF;

        chunks_arr := '[chunk_' || i || '_1, chunk_' || i || '_2]';

        r_id  := 'rag_run_'  || i || '_' || md5(('rag_r' || i)::text);
        sp_id := 'rag_sp_'   || i || '_' || md5(('rag_s' || i)::text);
        qid   := 'rag_q_'    || i || '_' || md5(('rag_q' || i)::text);

        INSERT INTO runs (run_id, tenant_id, framework, agent_id, model, start_time, end_time, status, tags, metadata, total_tokens, total_cost, latency_ms)
        VALUES (
            r_id,
            'tnt-c7ab1040eff7',
            frameworks[agent_idx],
            agents_rag[agent_idx],
            'text-embedding-3-small',
            start_ts,
            start_ts + (lat_ms || ' milliseconds')::INTERVAL,
            'SUCCESS',
            '{"env":"production","type":"rag"}',
            '{}',
            0, 0, lat_ms
        ) ON CONFLICT (run_id) DO NOTHING;

        INSERT INTO spans (span_id, run_id, span_name, kind, span_type, start_time, end_time, attributes, status)
        VALUES (
            sp_id, r_id,
            'rag.retrieve',
            'CLIENT',
            'rag',
            start_ts,
            start_ts + (lat_ms || ' milliseconds')::INTERVAL,
            jsonb_build_object(
                'rag.collection',        collections[col_idx],
                'rag.query_text',        query_texts[q_idx],
                'rag.top_k',             top_k_val,
                'rag.chunk_count',       chunk_cnt,
                'rag.similarity_scores', scores_arr,
                'rag.cache_hit',         hit_cache::TEXT
            ),
            'OK'
        ) ON CONFLICT (span_id) DO NOTHING;

        INSERT INTO rag_queries (
            query_id, tenant_id, span_id, run_id, query_text,
            retrieved_chunks, similarity_scores, latency_ms, metadata,
            context_precision, context_recall, faithfulness, answer_relevancy,
            chunk_count, collection, top_k
        ) VALUES (
            qid, 'tnt-c7ab1040eff7', sp_id, r_id, query_texts[q_idx],
            chunks_arr, scores_arr, lat_ms,
            jsonb_build_object('cache_hit', hit_cache::TEXT, 'agent', agents_rag[agent_idx]),
            prec, rec, faith, rel,
            chunk_cnt, collections[col_idx], top_k_val
        ) ON CONFLICT (query_id) DO NOTHING;

    END LOOP;
END $$;

-- ── 5. Seed drift snapshots (last 30 days, one per day per collection) ────────

DO $$
DECLARE
    collections TEXT[] := ARRAY['product_docs','support_kb','technical_specs','billing_docs','api_reference'];
    d INT;
    c INT;
    shift FLOAT;
    vol_delta FLOAT;
    prec_delta FLOAT;
    alert_lv TEXT;
    period_s TIMESTAMPTZ;
    period_e TIMESTAMPTZ;
BEGIN
    FOR d IN 0..29 LOOP
        FOR c IN 1..5 LOOP
            shift      := ROUND((0.01 + random() * 0.12)::NUMERIC, 4);
            vol_delta  := ROUND((-0.15 + random() * 0.30)::NUMERIC, 4);
            prec_delta := ROUND((-0.08 + random() * 0.16)::NUMERIC, 4);
            -- Alert when drift is significant
            alert_lv   := CASE
                WHEN shift > 0.10                        THEN 'critical'
                WHEN shift > 0.07 OR ABS(prec_delta) > 0.05 THEN 'warning'
                ELSE 'none'
            END;
            period_s := NOW() - ((d + 1) || ' days')::INTERVAL;
            period_e := NOW() - (d       || ' days')::INTERVAL;

            INSERT INTO rag_drift_snapshots (
                snapshot_id, tenant_id, collection,
                period_start, period_end,
                mean_cosine_shift, query_volume_delta, precision_delta,
                alert_level, metadata
            ) VALUES (
                'drift_' || d || '_' || c || '_' || md5((d * 10 + c)::TEXT),
                'tnt-c7ab1040eff7', collections[c],
                period_s, period_e,
                shift, vol_delta, prec_delta,
                alert_lv,
                jsonb_build_object('collection', collections[c], 'day_offset', d)
            ) ON CONFLICT (snapshot_id) DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

-- Enterprise SSO upgrade: persistent SP key, role/domain/attribute mappings, metadata XML upload

-- Persistent SP signing key per tenant (one key pair per tenant, shared across providers)
CREATE TABLE sso_sp_keys (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    private_key_encrypted   TEXT NOT NULL,
    cert_pem                TEXT NOT NULL,
    algorithm               VARCHAR(16) NOT NULL DEFAULT 'RSA',
    key_size_bits           INTEGER NOT NULL DEFAULT 2048,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id)
);

CREATE INDEX idx_sso_sp_keys_tenant ON sso_sp_keys(tenant_id);

-- SAML config enhancements
ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS role_mappings      JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS allowed_domains    JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS attribute_mappings JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS idp_cert_pem       TEXT;

ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS idp_metadata_xml   TEXT;

-- OIDC config enhancements
ALTER TABLE tenant_oauth_configs ADD COLUMN IF NOT EXISTS role_mappings      JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE tenant_oauth_configs ADD COLUMN IF NOT EXISTS allowed_domains    JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE tenant_oauth_configs ADD COLUMN IF NOT EXISTS attribute_mappings JSONB NOT NULL DEFAULT '{}'::jsonb;

-- 2. Migrate retention policies
UPDATE retention_policies 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 3. Migrate runs
UPDATE runs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 4. Migrate spans (if tenant_id exists, fallback safely)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'spans' AND column_name = 'tenant_id'
    ) THEN
        UPDATE spans 
        SET tenant_id = 'tnt-c7ab1040eff7' 
        WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';
    END IF;
END $$;

-- 5. Migrate RAG queries
UPDATE rag_queries 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 6. Migrate RAG drift snapshots
UPDATE rag_drift_snapshots 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 7. Migrate provenance entries
UPDATE provenance_entries 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 8. Migrate metric snapshots
UPDATE metric_snapshots 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 9. Migrate export configurations
UPDATE export_configs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 10. Migrate audit logs
UPDATE audit_logs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 11. Migrate alert rules
UPDATE alert_rules 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 12. Migrate alert events
UPDATE alert_events 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 13. Migrate datasets
UPDATE datasets 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 15. Migrate evaluation runs
UPDATE eval_runs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 16. Migrate replay runs
UPDATE replay_runs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 17. Migrate red team runs
UPDATE red_team_runs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 18. Migrate breakpoints
UPDATE breakpoints 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 19. Migrate users
UPDATE users 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 20. Migrate SCIM tokens
UPDATE scim_tokens 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 21. Migrate SSO configurations
UPDATE tenant_oauth_configs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

UPDATE tenant_saml_configs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 22. Seed Provenance data referencing updated runs
DO $$
DECLARE
    r RECORD;
    i INT := 1;
BEGIN
    FOR r IN (SELECT run_id, agent_id, start_time FROM runs) LOOP
        -- Node 1: RAG Query
        INSERT INTO provenance_entries (
            entry_id, run_id, agent_id, decision_type, input_state, 
            reasoning, output, parent_ids, timestamp, metadata, tenant_id
        )
        VALUES (
            'prov_rag_' || r.run_id,
            r.run_id,
            r.agent_id,
            'RAG_QUERY',
            '{"user_prompt": "Run diagnostic check for system state", "session_id": "sess_' || i || '"}',
            'Reasoning step ' || i || '.1: Querying semantic index for similar runs and agent patterns.',
            '{"status": "retrieved", "documents_found": 3, "score": 0.94}',
            '[]'::jsonb,
            r.start_time,
            '{"latency_ms": 45}'::jsonb,
            'tnt-c7ab1040eff7'
        ) ON CONFLICT (entry_id) DO NOTHING;

        -- Node 2: Model Call (depends on Node 1)
        INSERT INTO provenance_entries (
            entry_id, run_id, agent_id, decision_type, input_state, 
            reasoning, output, parent_ids, timestamp, metadata, tenant_id
        )
        VALUES (
            'prov_model_' || r.run_id,
            r.run_id,
            r.agent_id,
            'MODEL_CALL',
            '{"retrieved_context": "Found 3 diagnostic templates", "prompt": "Process state vector"}',
            'Reasoning step ' || i || '.2: Synthesizing context, calling model to determine system resolution step.',
            '{"status": "resolved", "action_required": "execute_system_flush", "confidence": 0.89}',
            ('["prov_rag_' || r.run_id || '"]')::jsonb,
            r.start_time + INTERVAL '50 milliseconds',
            '{"latency_ms": 120}'::jsonb,
            'tnt-c7ab1040eff7'
        ) ON CONFLICT (entry_id) DO NOTHING;

        -- Node 3: Tool Execution (depends on Node 2)
        INSERT INTO provenance_entries (
            entry_id, run_id, agent_id, decision_type, input_state, 
            reasoning, output, parent_ids, timestamp, metadata, tenant_id
        )
        VALUES (
            'prov_tool_' || r.run_id,
            r.run_id,
            r.agent_id,
            'TOOL_EXECUTION',
            '{"action": "execute_system_flush", "parameters": {}}',
            'Reasoning step ' || i || '.3: Executing system flush tool as determined by the model call.',
            '{"status": "success", "bytes_flushed": 4096}',
            ('["prov_model_' || r.run_id || '"]')::jsonb,
            r.start_time + INTERVAL '200 milliseconds',
            '{"latency_ms": 85}'::jsonb,
            'tnt-c7ab1040eff7'
        ) ON CONFLICT (entry_id) DO NOTHING;

        i := i + 1;
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS notification_deliveries (
    delivery_id    TEXT PRIMARY KEY,
    event_id       TEXT NOT NULL,
    channel_id     TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'pending', -- pending, sent, failed, dlq
    attempt_count  INT  NOT NULL DEFAULT 0,
    last_error     TEXT,
    sent_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notif_deliveries_event   ON notification_deliveries(event_id);

CREATE INDEX IF NOT EXISTS idx_notif_deliveries_channel ON notification_deliveries(channel_id);

CREATE INDEX IF NOT EXISTS idx_notif_deliveries_status  ON notification_deliveries(status);

CREATE TABLE IF NOT EXISTS pii_config (
    tenant_id   VARCHAR(64)  PRIMARY KEY,
    master_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    rules       TEXT         NOT NULL DEFAULT '[]',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

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

CREATE TABLE IF NOT EXISTS tenant_settings (
    tenant_id  VARCHAR(64)  NOT NULL,
    key        VARCHAR(128) NOT NULL,
    value      TEXT         NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, key)
);

ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS key_prefix VARCHAR(16);

