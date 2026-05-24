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
