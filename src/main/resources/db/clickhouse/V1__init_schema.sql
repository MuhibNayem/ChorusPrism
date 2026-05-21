-- ClickHouse schema for Chorus Observe Phase 1
-- Optimized for append-only, high-throughput span ingestion.
-- Run this manually against your ClickHouse instance.

CREATE TABLE IF NOT EXISTS ch_spans (
    span_id String,
    run_id String,
    parent_span_id Nullable(String),
    span_name String,
    kind String,
    start_time DateTime64(3),
    end_time Nullable(DateTime64(3)),
    attributes String,
    events String,
    status String
)
ENGINE = MergeTree()
ORDER BY (run_id, start_time)
TTL start_time + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS ch_llm_calls (
    call_id String,
    span_id String,
    run_id String,
    provider String,
    model String,
    input_tokens Int32,
    output_tokens Int32,
    cost_usd Decimal(18, 8),
    latency_ms Int64,
    prompt Nullable(String),
    completion Nullable(String),
    finish_reasons String,
    created_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = MergeTree()
ORDER BY (run_id, created_at)
TTL created_at + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS ch_tool_calls (
    call_id String,
    span_id String,
    run_id String,
    tool_name String,
    args Nullable(String),
    result Nullable(String),
    latency_ms Int64,
    error Nullable(String),
    created_at DateTime64(3) DEFAULT now64(3)
)
ENGINE = MergeTree()
ORDER BY (run_id, created_at)
TTL created_at + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;
