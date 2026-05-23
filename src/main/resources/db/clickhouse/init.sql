-- ClickHouse schema for Chorus Observe span storage
-- Optimized for high-throughput append-only ingestion

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
    status String,
    span_type Nullable(String),
    first_token_at Nullable(DateTime64(3)),
    ingested_at DateTime64(3) DEFAULT now()
) ENGINE = MergeTree()
ORDER BY (run_id, start_time, span_id);

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
    messages Nullable(String),
    ingested_at DateTime64(3) DEFAULT now()
) ENGINE = MergeTree()
ORDER BY (run_id, ingested_at, call_id);

CREATE TABLE IF NOT EXISTS ch_tool_calls (
    call_id String,
    span_id String,
    run_id String,
    tool_name String,
    args Nullable(String),
    result Nullable(String),
    latency_ms Int64,
    error Nullable(String),
    ingested_at DateTime64(3) DEFAULT now()
) ENGINE = MergeTree()
ORDER BY (run_id, ingested_at, call_id);
