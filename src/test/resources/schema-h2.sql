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
    snapshot_id VARCHAR(64) PRIMARY KEY,
    metric_name VARCHAR(256) NOT NULL,
    `value` DOUBLE NOT NULL,
    tags VARCHAR(4000) NOT NULL DEFAULT '{}',
    `timestamp` TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
