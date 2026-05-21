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
CREATE TABLE IF NOT EXISTS metric_snapshots (
    snapshot_id VARCHAR(64) PRIMARY KEY,
    metric_name VARCHAR(256) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    tags JSONB NOT NULL DEFAULT '{}',
    timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
