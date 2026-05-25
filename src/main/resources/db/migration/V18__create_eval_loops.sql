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
