-- H2-compatible schema for tests

CREATE TABLE IF NOT EXISTS runs (
    run_id VARCHAR(64) PRIMARY KEY,
    framework VARCHAR(64) NOT NULL,
    agent_id VARCHAR(256) NOT NULL,
    model VARCHAR(128),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    tags VARCHAR(4000) NOT NULL DEFAULT '{}',
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    total_tokens INT NOT NULL DEFAULT 0,
    total_cost DECIMAL(18, 8) NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spans (
    span_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    span_name VARCHAR(512) NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    attributes VARCHAR(4000) NOT NULL DEFAULT '{}',
    events VARCHAR(4000) NOT NULL DEFAULT '[]',
    status VARCHAR(16) NOT NULL DEFAULT 'UNSET',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS llm_calls (
    call_id VARCHAR(64) PRIMARY KEY,
    span_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    cost_usd DECIMAL(18, 8) NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    prompt CLOB,
    completion CLOB,
    finish_reasons VARCHAR(1000) NOT NULL DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tool_calls (
    call_id VARCHAR(64) PRIMARY KEY,
    span_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(256) NOT NULL,
    args CLOB,
    result CLOB,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    error CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS feedback (
    feedback_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(64),
    score DECIMAL(4, 2),
    label VARCHAR(128),
    comment CLOB,
    source VARCHAR(64) NOT NULL DEFAULT 'human',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS provenance_entries (
    entry_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(256) NOT NULL,
    decision_type VARCHAR(128) NOT NULL,
    input_state CLOB,
    reasoning CLOB,
    output CLOB,
    parent_ids VARCHAR(4000) NOT NULL DEFAULT '[]',
    timestamp TIMESTAMP NOT NULL,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS rag_queries (
    query_id VARCHAR(64) PRIMARY KEY,
    span_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    query_text CLOB NOT NULL,
    retrieved_chunks CLOB,
    similarity_scores CLOB,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metric_snapshots (
    snapshot_id VARCHAR(64) NOT NULL,
    metric_name VARCHAR(256) NOT NULL,
    `value` DOUBLE NOT NULL,
    tags VARCHAR(4000) NOT NULL DEFAULT '{}',
    `timestamp` TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_id, `timestamp`)
);

-- ============================================================
-- Phase 3-9: Evaluation, Time-Travel, Red Teaming, Monitoring, Prompts
-- ============================================================

CREATE TABLE IF NOT EXISTS datasets (
    dataset_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description CLOB,
    tags VARCHAR(4000) NOT NULL DEFAULT '{}',
    source VARCHAR(64) NOT NULL DEFAULT 'manual',
    split_config VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dataset_items (
    item_id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    input CLOB NOT NULL,
    expected_output CLOB,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    tags VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eval_runs (
    eval_run_id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    name VARCHAR(256),
    agent_config VARCHAR(4000) NOT NULL DEFAULT '{}',
    scorer_config VARCHAR(4000) NOT NULL DEFAULT '{}',
    parallelism INT NOT NULL DEFAULT 8,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    summary_metrics VARCHAR(4000) NOT NULL DEFAULT '{}',
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eval_results (
    result_id VARCHAR(64) PRIMARY KEY,
    eval_run_id VARCHAR(64) NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64),
    span_id VARCHAR(64),
    actual_output CLOB,
    score DOUBLE NOT NULL DEFAULT 0,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    reasoning CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS checkpoints (
    checkpoint_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    sequence INT NOT NULL,
    state_snapshot VARCHAR(4000) NOT NULL DEFAULT '{}',
    next_nodes VARCHAR(4000) NOT NULL DEFAULT '[]',
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (run_id, sequence)
);

CREATE TABLE IF NOT EXISTS replay_runs (
    replay_run_id VARCHAR(64) PRIMARY KEY,
    original_run_id VARCHAR(64) NOT NULL,
    from_checkpoint_id VARCHAR(64),
    state_overrides VARCHAR(4000) NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS breakpoints (
    breakpoint_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    before_node VARCHAR(256),
    before_tool VARCHAR(256),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS red_team_scenarios (
    scenario_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    category VARCHAR(128) NOT NULL,
    attack_prompt CLOB NOT NULL,
    expected_behavior CLOB,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS red_team_runs (
    red_team_run_id VARCHAR(64) PRIMARY KEY,
    agent_config VARCHAR(4000) NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    total_scenarios INT NOT NULL DEFAULT 0,
    bypassed_count INT NOT NULL DEFAULT 0,
    blocked_count INT NOT NULL DEFAULT 0,
    summary_metrics VARCHAR(4000) NOT NULL DEFAULT '{}',
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS red_team_results (
    result_id VARCHAR(64) PRIMARY KEY,
    red_team_run_id VARCHAR(64) NOT NULL,
    scenario_id VARCHAR(64) NOT NULL,
    agent_output CLOB,
    guardrail_result VARCHAR(4000) NOT NULL DEFAULT '{}',
    bypassed BOOLEAN NOT NULL DEFAULT FALSE,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS guardrail_telemetry (
    telemetry_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64),
    guardrail_name VARCHAR(256) NOT NULL,
    tier INT NOT NULL DEFAULT 1,
    action VARCHAR(16) NOT NULL,
    confidence DOUBLE,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS alert_rules (
    rule_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    condition_expr CLOB NOT NULL,
    threshold DOUBLE NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    webhook_url VARCHAR(512),
    email VARCHAR(256),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    cooldown_seconds INT NOT NULL DEFAULT 300,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS alert_events (
    event_id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    triggered_at TIMESTAMP NOT NULL,
    value DOUBLE NOT NULL,
    resolved_at TIMESTAMP,
    notification_sent BOOLEAN NOT NULL DEFAULT FALSE,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS trace_clusters (
    cluster_id VARCHAR(64) PRIMARY KEY,
    label VARCHAR(256) NOT NULL,
    description CLOB,
    run_count INT NOT NULL DEFAULT 0,
    avg_score DOUBLE,
    avg_cost DECIMAL(18, 8),
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS budget_enforcements (
    enforcement_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(256) NOT NULL,
    budget_type VARCHAR(64) NOT NULL DEFAULT 'per_run',
    limit_value DECIMAL(18, 8) NOT NULL,
    current_value DECIMAL(18, 8) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    triggered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prompt_versions (
    version_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    content CLOB NOT NULL,
    model VARCHAR(128),
    temperature DOUBLE,
    max_tokens INT,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_by VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prompt_tags (
    version_id VARCHAR(64) NOT NULL,
    tag_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (version_id, tag_name)
);

CREATE TABLE IF NOT EXISTS prompt_ab_tests (
    test_id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64),
    prompt_a_id VARCHAR(64) NOT NULL,
    prompt_b_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    winner_id VARCHAR(64),
    p_value DOUBLE,
    summary_metrics VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

-- ============================================================
-- V3 Enterprise Features (H2-compatible)
-- ============================================================

CREATE TABLE IF NOT EXISTS distributed_locks (
    lock_name       VARCHAR(256) PRIMARY KEY,
    owner_id        VARCHAR(256) NOT NULL,
    token_id        VARCHAR(64),
    acquired_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_distributed_locks_expires ON distributed_locks(expires_at);
CREATE INDEX IF NOT EXISTS idx_distributed_locks_token ON distributed_locks(token_id);

CREATE TABLE IF NOT EXISTS trace_embeddings (
    embedding_id    VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL,
    span_id         VARCHAR(64),
    model           VARCHAR(128) NOT NULL,
    vector          VARCHAR(4000) NOT NULL,
    text_source     CLOB NOT NULL,
    metadata        VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trace_embeddings_run ON trace_embeddings(run_id);
CREATE INDEX IF NOT EXISTS idx_trace_embeddings_model ON trace_embeddings(model);

CREATE TABLE IF NOT EXISTS multi_turn_scenarios (
    scenario_id     VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    description     CLOB,
    turns           VARCHAR(4000) NOT NULL,
    metadata        VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS multi_turn_runs (
    run_id          VARCHAR(64) PRIMARY KEY,
    scenario_id     VARCHAR(64) NOT NULL,
    agent_config    VARCHAR(4000) NOT NULL DEFAULT '{}',
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    total_turns     INT NOT NULL DEFAULT 0,
    passed_turns    INT NOT NULL DEFAULT 0,
    failed_turns    INT NOT NULL DEFAULT 0,
    final_score     DOUBLE,
    summary_metrics VARCHAR(4000) NOT NULL DEFAULT '{}',
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_multi_turn_runs_scenario ON multi_turn_runs(scenario_id);
CREATE INDEX IF NOT EXISTS idx_multi_turn_runs_status ON multi_turn_runs(status);

CREATE TABLE IF NOT EXISTS multi_turn_turns (
    turn_id         VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL,
    turn_index      INT NOT NULL,
    role            VARCHAR(32) NOT NULL,
    input_message   CLOB NOT NULL,
    agent_output    CLOB,
    expected_keywords VARCHAR(4000) NOT NULL DEFAULT '[]',
    matched_keywords VARCHAR(4000) NOT NULL DEFAULT '[]',
    score           DOUBLE,
    passed          BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms      BIGINT NOT NULL DEFAULT 0,
    metadata        VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_multi_turn_turns_run ON multi_turn_turns(run_id);

CREATE TABLE IF NOT EXISTS budget_events (
    event_id        VARCHAR(64) PRIMARY KEY,
    enforcement_id  VARCHAR(64) NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    amount          DECIMAL(18, 8) NOT NULL DEFAULT 0,
    currency        VARCHAR(8) NOT NULL DEFAULT 'USD',
    description     CLOB,
    metadata        VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_budget_events_enforcement ON budget_events(enforcement_id);
CREATE INDEX IF NOT EXISTS idx_budget_events_type ON budget_events(event_type);

-- ============================================================
-- V5 Enterprise: RBAC, Audit, Retention, Export, Notifications, Dashboards
-- ============================================================

CREATE TABLE IF NOT EXISTS tenants (
    tenant_id       VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    config          VARCHAR(4000) NOT NULL DEFAULT '{}',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    user_id         VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    email           VARCHAR(256) NOT NULL,
    password_hash   VARCHAR(256) NOT NULL,
    display_name    VARCHAR(256),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, email)
);

CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);

CREATE TABLE IF NOT EXISTS roles (
    role_id         VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    permissions     VARCHAR(4000) NOT NULL DEFAULT '[]',
    description     CLOB,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_roles_tenant ON roles(tenant_id);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id         VARCHAR(64) NOT NULL,
    role_id         VARCHAR(64) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS api_keys (
    key_hash        VARCHAR(256) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64),
    name            VARCHAR(256) NOT NULL,
    scopes          VARCHAR(4000) NOT NULL DEFAULT '["read"]',
    expires_at      TIMESTAMP,
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_api_keys_tenant ON api_keys(tenant_id);

CREATE TABLE IF NOT EXISTS audit_logs (
    log_id          VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64),
    action          VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,
    resource_id     VARCHAR(64),
    old_value       VARCHAR(4000),
    new_value       VARCHAR(4000),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    success         BOOLEAN NOT NULL DEFAULT TRUE,
    details         VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);

CREATE TABLE IF NOT EXISTS retention_policies (
    policy_id       VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,
    retention_days  INT NOT NULL,
    archive_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    archive_location VARCHAR(512),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMP,
    last_run_deleted BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_retention_policies_tenant ON retention_policies(tenant_id);

CREATE TABLE IF NOT EXISTS export_jobs (
    job_id          VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,
    query_filter    VARCHAR(4000) NOT NULL DEFAULT '{}',
    format          VARCHAR(16) NOT NULL,
    destination     VARCHAR(16) NOT NULL,
    destination_path VARCHAR(512),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    total_records   BIGINT,
    file_size_bytes BIGINT,
    error_message   CLOB,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_export_jobs_tenant ON export_jobs(tenant_id);

CREATE TABLE IF NOT EXISTS notification_channels (
    channel_id      VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    channel_type    VARCHAR(32) NOT NULL,
    config          VARCHAR(4000) NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_channels_tenant ON notification_channels(tenant_id);

CREATE TABLE IF NOT EXISTS alert_rule_channels (
    rule_id         VARCHAR(64) NOT NULL,
    channel_id      VARCHAR(64) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id, channel_id)
);

CREATE TABLE IF NOT EXISTS dashboards (
    dashboard_id    VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    description     CLOB,
    layout          VARCHAR(4000) NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dashboards_tenant ON dashboards(tenant_id);

CREATE TABLE IF NOT EXISTS dashboard_widgets (
    widget_id       VARCHAR(64) PRIMARY KEY,
    dashboard_id    VARCHAR(64) NOT NULL,
    widget_type     VARCHAR(32) NOT NULL,
    title           VARCHAR(256) NOT NULL,
    query_config    VARCHAR(4000) NOT NULL DEFAULT '{}',
    position        VARCHAR(4000) NOT NULL DEFAULT '{}',
    refresh_seconds INT NOT NULL DEFAULT 60,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dashboard_widgets_dashboard ON dashboard_widgets(dashboard_id);

CREATE TABLE IF NOT EXISTS ingestion_queue (
    queue_id        VARCHAR(64) PRIMARY KEY,
    trace_id        VARCHAR(64) NOT NULL,
    span_id         VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    start_time      TIMESTAMP NOT NULL,
    end_time        TIMESTAMP,
    kind            INT NOT NULL,
    status_code     INT NOT NULL,
    attributes      JSONB NOT NULL,
    events          JSONB NOT NULL DEFAULT '[]',
    parent_span_id  VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ingestion_queue_created ON ingestion_queue(created_at);

-- V14: Auth security hardening tables (H2 compatible)

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              VARCHAR(36) PRIMARY KEY DEFAULT RANDOM_UUID(),
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    jti             VARCHAR(64) NOT NULL UNIQUE,
    expires_at      TIMESTAMP NOT NULL,
    revoked_at      TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS revoked_tokens (
    jti         VARCHAR(64) PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    revoked_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS login_attempts (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    identifier      VARCHAR(320) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT '',
    attempt_count   INT NOT NULL DEFAULT 1,
    window_start    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until    TIMESTAMP,
    last_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_login_attempts UNIQUE (identifier, tenant_id)
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          VARCHAR(36) PRIMARY KEY DEFAULT RANDOM_UUID(),
    user_id     VARCHAR(64) NOT NULL,
    tenant_id   VARCHAR(64) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id          VARCHAR(36) PRIMARY KEY DEFAULT RANDOM_UUID(),
    user_id     VARCHAR(64) NOT NULL,
    tenant_id   VARCHAR(64) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
