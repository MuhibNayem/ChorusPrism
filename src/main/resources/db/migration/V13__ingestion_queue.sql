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
