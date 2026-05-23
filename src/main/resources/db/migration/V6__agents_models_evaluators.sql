-- ============================================================
-- Agents, Models, Evaluators, Span Types, LLM Messages
-- ============================================================

-- ============================================================
-- Agents
-- ============================================================
CREATE TABLE IF NOT EXISTS agents (
    agent_id        VARCHAR(256) PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    description     TEXT,
    framework       VARCHAR(64),
    runtime         VARCHAR(128),
    owner           VARCHAR(128),
    owner_email     VARCHAR(256),
    tags            JSONB NOT NULL DEFAULT '[]',
    version         VARCHAR(32),
    deployed_at     TIMESTAMPTZ,
    deployed_by     VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'healthy',
    health          DECIMAL(5,2),
    repo            VARCHAR(512),
    branch          VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agents_status ON agents(status);
CREATE INDEX IF NOT EXISTS idx_agents_framework ON agents(framework);

-- ============================================================
-- Evaluators
-- ============================================================
CREATE TABLE IF NOT EXISTS evaluators (
    evaluator_id    VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    kind            VARCHAR(32) NOT NULL,
    description     TEXT,
    config          JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evaluators_kind ON evaluators(kind);

-- ============================================================
-- Run Evaluations
-- ============================================================
CREATE TABLE IF NOT EXISTS run_evaluations (
    evaluation_id   VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES runs(run_id) ON DELETE CASCADE,
    evaluator_id    VARCHAR(64) NOT NULL REFERENCES evaluators(evaluator_id),
    score           DECIMAL(4,3) NOT NULL,
    passed          BOOLEAN NOT NULL DEFAULT FALSE,
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_run_evaluations_run ON run_evaluations(run_id);
CREATE INDEX IF NOT EXISTS idx_run_evaluations_evaluator ON run_evaluations(evaluator_id);

-- ============================================================
-- Span enhancements
-- ============================================================
ALTER TABLE spans ADD COLUMN IF NOT EXISTS span_type VARCHAR(16);
ALTER TABLE spans ADD COLUMN IF NOT EXISTS first_token_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_spans_type ON spans(span_type);

-- ============================================================
-- LLM call enhancements
-- ============================================================
ALTER TABLE llm_calls ADD COLUMN IF NOT EXISTS messages JSONB;

-- ============================================================
-- Seed evaluators
-- ============================================================
INSERT INTO evaluators (evaluator_id, name, kind, description, config)
VALUES
    ('ev_helpfulness',  'helpfulness',          'llm-judge', 'Evaluates how helpful the completion is to the user.',              '{}'),
    ('ev_groundedness', 'groundedness',         'llm-judge', 'Checks that the completion is grounded in the provided context.',   '{}'),
    ('ev_latency_sla',  'latency < 3s',         'rule',      'Checks that latency is under 3 seconds.',                           '{"threshold_ms": 3000}'),
    ('ev_no_pii',       'no PII in completion', 'regex',     'Scans completion for personally identifiable information.',         '{}')
ON CONFLICT (evaluator_id) DO NOTHING;
