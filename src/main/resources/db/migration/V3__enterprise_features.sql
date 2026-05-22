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
