-- ============================================================
-- Demo seed data — idempotent, runs once via Flyway
-- Provides realistic data for all dashboard views
-- ============================================================

-- ============================================================
-- Tenant (required by retention_policies)
-- ============================================================
INSERT INTO tenants (tenant_id, name, config)
VALUES ('tenant_demo', 'Demo Org', '{"plan":"enterprise","max_users":50}')
ON CONFLICT (tenant_id) DO NOTHING;

-- ============================================================
-- Agents
-- ============================================================
INSERT INTO agents (agent_id, name, description, framework, runtime, owner, owner_email, tags, version, deployed_at, deployed_by, status, health, repo, branch)
VALUES
    ('agent_support_bot',    'Customer Support Bot',   'Handles tier-1 customer queries using RAG over KB.',       'langchain',   'python3.11', 'platform-team', 'platform@chorus.ai', '["nlp","rag","production"]',        'v2.4.1', NOW() - INTERVAL '5 days',  'ci-deploy',  'healthy',  0.97, 'github.com/chorus/support-bot',    'main'),
    ('agent_code_reviewer',  'Code Review Assistant',  'Reviews PRs and suggests improvements using GPT-4o.',      'openai-sdk',  'python3.12', 'devtools-team', 'devtools@chorus.ai', '["code","review","production"]',    'v1.8.3', NOW() - INTERVAL '12 days', 'ci-deploy',  'healthy',  0.94, 'github.com/chorus/code-reviewer',  'main'),
    ('agent_data_pipeline',  'Data Pipeline Agent',    'Orchestrates ETL jobs and monitors data quality.',         'crewai',      'python3.11', 'data-team',     'data@chorus.ai',     '["etl","data","production"]',       'v3.1.0', NOW() - INTERVAL '3 days',  'manual',     'healthy',  0.99, 'github.com/chorus/data-pipeline',  'main'),
    ('agent_content_gen',    'Content Generator',      'Generates marketing copy and blog posts via Claude.',      'anthropic',   'python3.12', 'marketing-team','marketing@chorus.ai','["content","generation","staging"]','v1.2.0', NOW() - INTERVAL '8 days',  'ci-deploy',  'degraded', 0.81, 'github.com/chorus/content-gen',    'staging'),
    ('agent_anomaly_detect', 'Anomaly Detector',       'Detects anomalies in time-series metrics using LLM.',     'haystack',    'python3.10', 'ops-team',      'ops@chorus.ai',      '["monitoring","ops","production"]', 'v4.0.2', NOW() - INTERVAL '1 day',   'ci-deploy',  'healthy',  0.92, 'github.com/chorus/anomaly-detect', 'main')
ON CONFLICT (agent_id) DO NOTHING;

-- ============================================================
-- Runs — 500 runs spread over 30 days across all 5 agents
-- ============================================================
DO $$
DECLARE
    agents      TEXT[]  := ARRAY['agent_support_bot','agent_code_reviewer','agent_data_pipeline','agent_content_gen','agent_anomaly_detect'];
    frameworks  TEXT[]  := ARRAY['langchain','openai-sdk','crewai','anthropic','haystack'];
    models      TEXT[]  := ARRAY['claude-3-5-sonnet-20241022','gpt-4o','claude-3-haiku-20240307','gpt-4o-mini','gemini-1.5-pro'];
    providers   TEXT[]  := ARRAY['anthropic','openai','anthropic','openai','google'];
    tools       TEXT[]  := ARRAY['search_kb','write_review','query_db','generate_copy','detect_anomaly','send_email','fetch_data','summarize'];
    i           INT;
    agent_idx   INT;
    start_ts    TIMESTAMPTZ;
    lat_ms      BIGINT;
    in_tok      INT;
    out_tok     INT;
    cost        DECIMAL(18,8);
    run_status  TEXT;
    r_id        TEXT;
    sp_id       TEXT;
    lc_id       TEXT;
    tc_id       TEXT;
    hour_offset FLOAT;
BEGIN
    FOR i IN 1..500 LOOP
        agent_idx  := (i % 5) + 1;
        hour_offset := random() * 720;  -- 30 days
        start_ts   := NOW() - (hour_offset || ' hours')::INTERVAL;
        lat_ms     := (500 + random() * 9500)::BIGINT;  -- 500ms–10s
        in_tok     := (200 + random() * 1800)::INT;
        out_tok    := (50  + random() * 950)::INT;
        cost       := ROUND((in_tok * 0.000003 + out_tok * 0.000015)::NUMERIC, 8);
        run_status := CASE WHEN random() < 0.07 THEN 'ERROR' ELSE 'SUCCESS' END;

        r_id := 'run_' || i || '_' || md5(i::text);
        sp_id := 'sp_' || i || '_' || md5((i+1000)::text);
        lc_id := 'lc_' || i || '_' || md5((i+2000)::text);
        tc_id := 'tc_' || i || '_' || md5((i+3000)::text);

        INSERT INTO runs (run_id, framework, agent_id, model, start_time, end_time, status, tags, metadata, total_tokens, total_cost, latency_ms)
        VALUES (
            r_id,
            frameworks[agent_idx],
            agents[agent_idx],
            models[agent_idx],
            start_ts,
            start_ts + (lat_ms || ' milliseconds')::INTERVAL,
            run_status,
            '{"env":"production"}',
            '{}',
            in_tok + out_tok,
            cost,
            lat_ms
        )
        ON CONFLICT (run_id) DO NOTHING;

        INSERT INTO spans (span_id, run_id, span_name, kind, start_time, end_time, status)
        VALUES (
            sp_id,
            r_id,
            'llm.generate',
            'CLIENT',
            start_ts,
            start_ts + (lat_ms || ' milliseconds')::INTERVAL,
            CASE WHEN run_status = 'ERROR' THEN 'ERROR' ELSE 'OK' END
        )
        ON CONFLICT (span_id) DO NOTHING;

        INSERT INTO llm_calls (call_id, span_id, run_id, provider, model, input_tokens, output_tokens, cost_usd, latency_ms, prompt, completion)
        VALUES (
            lc_id,
            sp_id,
            r_id,
            providers[agent_idx],
            models[agent_idx],
            in_tok,
            out_tok,
            cost,
            lat_ms,
            'User: [query ' || i || ']',
            CASE WHEN run_status = 'ERROR' THEN NULL ELSE 'Assistant: [response ' || i || ']' END
        )
        ON CONFLICT (call_id) DO NOTHING;

        INSERT INTO tool_calls (call_id, span_id, run_id, tool_name, args, result, latency_ms, error)
        VALUES (
            tc_id,
            sp_id,
            r_id,
            tools[((i % array_length(tools, 1)) + 1)],
            '{"query": "item ' || i || '"}',
            CASE WHEN run_status = 'ERROR' THEN NULL ELSE '{"result": "ok"}' END,
            (lat_ms / 3)::BIGINT,
            CASE WHEN run_status = 'ERROR' THEN 'TimeoutError: upstream service unreachable' ELSE NULL END
        )
        ON CONFLICT (call_id) DO NOTHING;

        -- Evaluations on ~60% of SUCCESS runs
        IF run_status = 'SUCCESS' AND random() < 0.6 THEN
            INSERT INTO run_evaluations (evaluation_id, run_id, evaluator_id, score, passed, details)
            VALUES (
                'eval_' || i || '_' || md5((i+4000)::text),
                r_id,
                CASE (i % 4)
                    WHEN 0 THEN 'ev_helpfulness'
                    WHEN 1 THEN 'ev_groundedness'
                    WHEN 2 THEN 'ev_latency_sla'
                    ELSE        'ev_no_pii'
                END,
                ROUND((0.70 + random() * 0.30)::NUMERIC, 3),
                lat_ms < 3000,
                '{}'
            )
            ON CONFLICT (evaluation_id) DO NOTHING;
        END IF;
    END LOOP;
END;
$$;

-- ============================================================
-- Datasets
-- ============================================================
INSERT INTO datasets (dataset_id, name, description, tags, source, updated_at)
VALUES
    ('ds_support_qa',    'Support Q&A Gold Set',     'Human-verified question/answer pairs from Tier-1 support.',  '{"owner":"platform-team","domain":"support"}',    'manual',   NOW() - INTERVAL '2 hours'),
    ('ds_code_review',   'Code Review Samples',      'PR diffs with expert review annotations.',                   '{"owner":"devtools-team","domain":"code"}',        'traces',   NOW() - INTERVAL '3 days'),
    ('ds_content_bench', 'Content Generation Bench', 'Marketing copy samples with human preference labels.',       '{"owner":"marketing-team","domain":"content"}',   'upload',   NOW() - INTERVAL '1 day'),
    ('ds_anomaly_eval',  'Anomaly Detection Eval',   'Time-series segments labelled with anomaly ground truth.',   '{"owner":"ops-team","domain":"monitoring"}',       'manual',   NOW() - INTERVAL '6 hours')
ON CONFLICT (dataset_id) DO NOTHING;

-- Dataset items
INSERT INTO dataset_items (item_id, dataset_id, input, expected_output, metadata)
SELECT
    'item_' || ds || '_' || n,
    ds,
    'Input example ' || n || ' for dataset ' || ds,
    'Expected output ' || n,
    '{}'
FROM (
    VALUES
        ('ds_support_qa'),
        ('ds_code_review'),
        ('ds_content_bench'),
        ('ds_anomaly_eval')
) AS t(ds),
generate_series(1, 50) AS n
ON CONFLICT (item_id) DO NOTHING;

-- ============================================================
-- Alert rules
-- ============================================================
INSERT INTO alert_rules (rule_id, name, condition_expr, threshold, severity, enabled)
VALUES
    ('rule_error_rate',   'High Error Rate',        'error_rate_pct > threshold',   5.0,  'error',   TRUE),
    ('rule_latency_p95',  'P95 Latency Breach',     'latency_p95_ms > threshold',   5000, 'warning', TRUE),
    ('rule_cost_spike',   'Daily Cost Spike',       'cost_24h_usd > threshold',     50.0, 'warning', TRUE)
ON CONFLICT (rule_id) DO NOTHING;

-- Alert events (4 recent firings)
INSERT INTO alert_events (event_id, rule_id, triggered_at, value, resolved_at, notification_sent)
VALUES
    ('evt_001', 'rule_error_rate',  NOW() - INTERVAL '6 hours',  8.3,  NOW() - INTERVAL '5 hours', TRUE),
    ('evt_002', 'rule_latency_p95', NOW() - INTERVAL '2 hours',  6120, NULL,                        FALSE),
    ('evt_003', 'rule_error_rate',  NOW() - INTERVAL '18 hours', 6.1,  NOW() - INTERVAL '17 hours', TRUE),
    ('evt_004', 'rule_cost_spike',  NOW() - INTERVAL '1 day',    61.4, NOW() - INTERVAL '23 hours', TRUE)
ON CONFLICT (event_id) DO NOTHING;

-- ============================================================
-- Retention policies (requires tenant)
-- ============================================================
INSERT INTO retention_policies (policy_id, tenant_id, name, resource_type, retention_days, archive_enabled, enabled)
VALUES
    ('rp_runs',        'tenant_demo', 'Run traces',        'runs',          90,  TRUE,  TRUE),
    ('rp_spans',       'tenant_demo', 'Span data',         'spans',         90,  TRUE,  TRUE),
    ('rp_eval',        'tenant_demo', 'Eval results',      'eval_results',  180, FALSE, TRUE),
    ('rp_alert_evts',  'tenant_demo', 'Alert events',      'alert_events',  30,  FALSE, TRUE)
ON CONFLICT (policy_id) DO NOTHING;
