-- Wave 1 Security Hardening: Add tenant_id to tables missing tenant isolation
-- All existing rows are assigned to the 'default' tenant.

-- ============================================================
-- Phase 2 tables (Evaluation, Datasets)
-- ============================================================
ALTER TABLE datasets ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_datasets_tenant ON datasets(tenant_id);

ALTER TABLE dataset_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_dataset_items_tenant ON dataset_items(tenant_id);

ALTER TABLE eval_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_eval_runs_tenant ON eval_runs(tenant_id);

ALTER TABLE eval_results ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_eval_results_tenant ON eval_results(tenant_id);

-- ============================================================
-- Phase 3 tables (Time-Travel)
-- ============================================================
ALTER TABLE checkpoints ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_checkpoints_tenant ON checkpoints(tenant_id);

ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_replay_runs_tenant ON replay_runs(tenant_id);

ALTER TABLE breakpoints ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_breakpoints_tenant ON breakpoints(tenant_id);

-- ============================================================
-- Phase 4 tables (Red Teaming)
-- ============================================================
ALTER TABLE red_team_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_red_team_runs_tenant ON red_team_runs(tenant_id);

ALTER TABLE red_team_results ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_red_team_results_tenant ON red_team_results(tenant_id);

-- red_team_scenarios are shared templates — no tenant isolation needed

-- ============================================================
-- Phase 5 tables (Monitoring, Alerts, Budgets)
-- ============================================================
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_alert_rules_tenant ON alert_rules(tenant_id);

ALTER TABLE alert_events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_alert_events_tenant ON alert_events(tenant_id);

ALTER TABLE trace_clusters ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_trace_clusters_tenant ON trace_clusters(tenant_id);

ALTER TABLE budget_enforcements ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_budget_enforcements_tenant ON budget_enforcements(tenant_id);

ALTER TABLE budget_events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_budget_events_tenant ON budget_events(tenant_id);

ALTER TABLE guardrail_telemetry ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_guardrail_telemetry_tenant ON guardrail_telemetry(tenant_id);

-- ============================================================
-- Phase 6 tables (Agents, Models, Evaluators)
-- ============================================================
ALTER TABLE agents ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_agents_tenant ON agents(tenant_id);

-- evaluators are shared templates — no tenant isolation needed

-- ============================================================
-- Phase 7 tables (Prompts)
-- ============================================================
ALTER TABLE prompt_versions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_prompt_versions_tenant ON prompt_versions(tenant_id);

ALTER TABLE prompt_ab_tests ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_prompt_ab_tests_tenant ON prompt_ab_tests(tenant_id);

-- prompt_tags inherits tenant via prompt_versions — no direct tenant_id needed

-- ============================================================
-- Phase 8 tables (Multi-Turn)
-- ============================================================
ALTER TABLE multi_turn_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_multi_turn_runs_tenant ON multi_turn_runs(tenant_id);

ALTER TABLE multi_turn_turns ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_multi_turn_turns_tenant ON multi_turn_turns(tenant_id);

-- multi_turn_scenarios are shared templates — no tenant isolation needed

-- ============================================================
-- Phase 9 tables (Trace Embeddings, Provenance, RAG)
-- ============================================================
ALTER TABLE trace_embeddings ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_trace_embeddings_tenant ON trace_embeddings(tenant_id);

ALTER TABLE provenance_entries ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_provenance_entries_tenant ON provenance_entries(tenant_id);

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_rag_queries_tenant ON rag_queries(tenant_id);

-- ============================================================
-- Phase 10 tables (Generated Eval Cases, Run Evaluations)
-- ============================================================
ALTER TABLE generated_eval_cases ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_generated_eval_cases_tenant ON generated_eval_cases(tenant_id);

ALTER TABLE run_evaluations ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_run_evaluations_tenant ON run_evaluations(tenant_id);

ALTER TABLE eval_result_runs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_eval_result_runs_tenant ON eval_result_runs(tenant_id);

-- ============================================================
-- Child tables of runs (spans, llm_calls, tool_calls, feedback)
-- Tenant isolation is enforced via JOIN with runs.tenant_id
-- No direct tenant_id column needed — keeps FK cascade semantics clean
-- ============================================================

-- ============================================================
-- Add composite indexes for common tenant-scoped queries
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_datasets_tenant_created ON datasets(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_eval_runs_tenant_status ON eval_runs(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_alert_rules_tenant_enabled ON alert_rules(tenant_id, enabled);
CREATE INDEX IF NOT EXISTS idx_prompt_versions_tenant_created ON prompt_versions(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agents_tenant_status ON agents(tenant_id, status);
