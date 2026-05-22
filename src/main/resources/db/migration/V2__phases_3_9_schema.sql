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
