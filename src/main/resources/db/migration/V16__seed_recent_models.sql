-- Seed runs with current 2025/2026 model names so the models page shows them.
-- Uses the same agents seeded in V15. ON CONFLICT DO NOTHING is idempotent.

DO $$
DECLARE
    recent_models  TEXT[]  := ARRAY[
        'claude-opus-4-7', 'claude-sonnet-4-6', 'claude-haiku-4-5',
        'o3', 'o4-mini', 'gpt-4.5',
        'gemini-2.5-pro', 'gemini-2.0-flash',
        'deepseek-v3', 'deepseek-r1',
        'llama-4-maverick', 'mistral-large-2'
    ];
    providers      TEXT[]  := ARRAY[
        'anthropic', 'anthropic', 'anthropic',
        'openai',    'openai',    'openai',
        'google',    'google',
        'deepseek',  'deepseek',
        'meta',      'mistral'
    ];
    agents         TEXT[]  := ARRAY[
        'agent_support_bot','agent_code_reviewer','agent_data_pipeline',
        'agent_content_gen','agent_anomaly_detect'
    ];
    frameworks     TEXT[]  := ARRAY['langchain','openai-sdk','crewai','anthropic','haystack'];
    i              INT;
    m_idx          INT;
    a_idx          INT;
    start_ts       TIMESTAMPTZ;
    lat_ms         BIGINT;
    in_tok         INT;
    out_tok        INT;
    cost           DECIMAL(18,8);
    run_status     TEXT;
    r_id           TEXT;
    sp_id          TEXT;
    lc_id          TEXT;
BEGIN
    -- 30 runs per model → 360 total, spread across last 7 days
    FOR i IN 1..360 LOOP
        m_idx     := ((i - 1) % 12) + 1;
        a_idx     := ((i - 1) % 5)  + 1;
        start_ts  := NOW() - ((random() * 168) || ' hours')::INTERVAL;
        lat_ms    := (400 + random() * 8000)::BIGINT;
        in_tok    := (300 + random() * 2000)::INT;
        out_tok   := (60  + random() * 800)::INT;
        cost      := ROUND((in_tok * 0.000003 + out_tok * 0.000015)::NUMERIC, 8);
        run_status := CASE WHEN random() < 0.06 THEN 'ERROR' ELSE 'SUCCESS' END;

        r_id  := 'run_r_' || i || '_' || md5((i + 10000)::text);
        sp_id := 'sp_r_'  || i || '_' || md5((i + 11000)::text);
        lc_id := 'lc_r_'  || i || '_' || md5((i + 12000)::text);

        INSERT INTO runs (run_id, framework, agent_id, model, start_time, end_time, status, tags, metadata, total_tokens, total_cost, latency_ms)
        VALUES (
            r_id,
            frameworks[a_idx],
            agents[a_idx],
            recent_models[m_idx],
            start_ts,
            start_ts + (lat_ms || ' milliseconds')::INTERVAL,
            run_status, '{"env":"production"}', '{}',
            in_tok + out_tok, cost, lat_ms
        )
        ON CONFLICT (run_id) DO NOTHING;

        INSERT INTO spans (span_id, run_id, span_name, kind, start_time, end_time, status)
        VALUES (
            sp_id, r_id, 'llm.generate', 'CLIENT',
            start_ts, start_ts + (lat_ms || ' milliseconds')::INTERVAL,
            CASE WHEN run_status = 'ERROR' THEN 'ERROR' ELSE 'OK' END
        )
        ON CONFLICT (span_id) DO NOTHING;

        INSERT INTO llm_calls (call_id, span_id, run_id, provider, model, input_tokens, output_tokens, cost_usd, latency_ms, prompt, completion)
        VALUES (
            lc_id, sp_id, r_id,
            providers[m_idx],
            recent_models[m_idx],
            in_tok, out_tok, cost, lat_ms,
            'User: [recent model run ' || i || ']',
            CASE WHEN run_status = 'ERROR' THEN NULL ELSE 'Assistant: [response]' END
        )
        ON CONFLICT (call_id) DO NOTHING;
    END LOOP;
END;
$$;
