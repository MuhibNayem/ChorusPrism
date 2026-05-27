-- ============================================================
-- V19: World-class RAG Analytics
--   • Fixes tenant_id gap in rag_queries
--   • Adds RAGAS-style scoring columns
--   • Adds rag_drift_snapshots for embedding drift detection
--   • Seeds 250 realistic RAG queries across 5 collections / 30 days
-- ============================================================

-- ── 1. Tenant isolation fix ───────────────────────────────────────────────────

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_rag_queries_tenant      ON rag_queries(tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_queries_tenant_time ON rag_queries(tenant_id, created_at DESC);

-- ── 2. RAGAS scoring columns ──────────────────────────────────────────────────

ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS context_precision  FLOAT;
ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS context_recall     FLOAT;
ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS faithfulness       FLOAT;
ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS answer_relevancy   FLOAT;
ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS chunk_count        INT  NOT NULL DEFAULT 0;
ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS collection         VARCHAR(256);
ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS top_k              INT  NOT NULL DEFAULT 5;
ALTER TABLE rag_queries ADD COLUMN IF NOT EXISTS query_embedding    JSONB;

CREATE INDEX IF NOT EXISTS idx_rag_queries_collection  ON rag_queries(collection) WHERE collection IS NOT NULL;

-- ── 3. Embedding drift snapshots ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS rag_drift_snapshots (
    snapshot_id      VARCHAR(64)  PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    collection       VARCHAR(256),
    period_start     TIMESTAMPTZ  NOT NULL,
    period_end       TIMESTAMPTZ  NOT NULL,
    mean_cosine_shift  FLOAT      NOT NULL DEFAULT 0.0,
    query_volume_delta FLOAT      NOT NULL DEFAULT 0.0,
    precision_delta    FLOAT      NOT NULL DEFAULT 0.0,
    alert_level      VARCHAR(16)  NOT NULL DEFAULT 'none',
    metadata         JSONB        NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rag_drift_tenant_time ON rag_drift_snapshots(tenant_id, created_at DESC);

-- ── 4. Seed data ──────────────────────────────────────────────────────────────

DO $$
DECLARE
    collections   TEXT[] := ARRAY['product_docs','support_kb','technical_specs','billing_docs','api_reference'];
    query_texts   TEXT[] := ARRAY[
        'How do I reset my password?',
        'What is the API rate limit for free tier?',
        'Explain the enterprise pricing model',
        'How to integrate with Slack webhooks?',
        'Troubleshoot 503 service unavailable errors',
        'What are the data retention policies?',
        'How to export my data to CSV?',
        'What SLA guarantees are provided?',
        'How does SSO login work?',
        'Describe the billing cycle and invoicing',
        'How to set up SCIM provisioning?',
        'What encryption standards are used?',
        'How to configure custom domains?',
        'What are the token usage limits?',
        'How to create an API key?',
        'Explain the retry logic for failed requests',
        'How to enable MFA for my account?',
        'What is the maximum file upload size?',
        'How to migrate from v1 to v2 API?',
        'Describe the audit log format',
        'How to set up cost alerts?',
        'What regions are supported for deployment?',
        'How to clone a workspace?',
        'Explain semantic search vs keyword search',
        'How to use the batch processing endpoint?',
        'What are the compliance certifications?',
        'How to handle webhook signature verification?',
        'What is the context window limit?',
        'How to enable verbose logging?',
        'Describe the agent memory architecture'
    ];
    agents_rag   TEXT[] := ARRAY['agent_support_bot','agent_support_bot','agent_support_bot',
                                  'agent_code_reviewer','agent_data_pipeline'];
    frameworks   TEXT[] := ARRAY['langchain','langchain','langchain','openai-sdk','crewai'];
    i            INT;
    q_idx        INT;
    col_idx      INT;
    agent_idx    INT;
    hour_offset  FLOAT;
    start_ts     TIMESTAMPTZ;
    lat_ms       BIGINT;
    prec         FLOAT;
    rec          FLOAT;
    faith        FLOAT;
    rel          FLOAT;
    chunk_cnt    INT;
    top_k_val    INT;
    r_id         TEXT;
    sp_id        TEXT;
    qid          TEXT;
    scores_arr   TEXT;
    chunks_arr   TEXT;
    hit_cache    BOOLEAN;
BEGIN
    FOR i IN 1..250 LOOP
        q_idx      := (i % array_length(query_texts, 1)) + 1;
        col_idx    := (i % 5) + 1;
        agent_idx  := (i % 5) + 1;
        hour_offset := random() * 720;
        start_ts   := NOW() - (hour_offset || ' hours')::INTERVAL;

        -- Realistic RAG latency: bimodal — cache hits fast, misses slower
        hit_cache  := random() < 0.38;
        lat_ms     := CASE WHEN hit_cache THEN (20 + random() * 80)::BIGINT
                           ELSE (80 + random() * 420)::BIGINT END;

        chunk_cnt  := (3 + random() * 7)::INT;
        top_k_val  := chunk_cnt + (random() * 2)::INT;

        -- Similarity scores array as text "[s1, s2, ...]"
        scores_arr := '[';
        FOR j IN 1..chunk_cnt LOOP
            IF j > 1 THEN scores_arr := scores_arr || ', '; END IF;
            scores_arr := scores_arr || ROUND((0.65 + random() * 0.32)::NUMERIC, 4)::TEXT;
        END LOOP;
        scores_arr := scores_arr || ']';

        -- Context precision: avg of scores (0.72–0.96 range for good RAG)
        prec  := ROUND((0.72 + random() * 0.24)::NUMERIC, 4);
        -- Context recall: fraction of needed context retrieved
        rec   := ROUND((0.64 + random() * 0.28)::NUMERIC, 4);
        -- Faithfulness: is answer grounded in context?
        faith := ROUND((0.70 + random() * 0.27)::NUMERIC, 4);
        -- Answer relevancy: semantic closeness of answer to query
        rel   := ROUND((0.75 + random() * 0.22)::NUMERIC, 4);

        -- Occasional low-quality retrieval (8% of queries)
        IF random() < 0.08 THEN
            prec  := ROUND((0.40 + random() * 0.25)::NUMERIC, 4);
            rec   := ROUND((0.35 + random() * 0.25)::NUMERIC, 4);
            faith := ROUND((0.42 + random() * 0.25)::NUMERIC, 4);
        END IF;

        chunks_arr := '[chunk_' || i || '_1, chunk_' || i || '_2]';

        r_id  := 'rag_run_'  || i || '_' || md5(('rag_r' || i)::text);
        sp_id := 'rag_sp_'   || i || '_' || md5(('rag_s' || i)::text);
        qid   := 'rag_q_'    || i || '_' || md5(('rag_q' || i)::text);

        INSERT INTO runs (run_id, tenant_id, framework, agent_id, model, start_time, end_time, status, tags, metadata, total_tokens, total_cost, latency_ms)
        VALUES (
            r_id,
            'tnt-c7ab1040eff7',
            frameworks[agent_idx],
            agents_rag[agent_idx],
            'text-embedding-3-small',
            start_ts,
            start_ts + (lat_ms || ' milliseconds')::INTERVAL,
            'SUCCESS',
            '{"env":"production","type":"rag"}',
            '{}',
            0, 0, lat_ms
        ) ON CONFLICT (run_id) DO NOTHING;

        INSERT INTO spans (span_id, run_id, span_name, kind, span_type, start_time, end_time, attributes, status)
        VALUES (
            sp_id, r_id,
            'rag.retrieve',
            'CLIENT',
            'rag',
            start_ts,
            start_ts + (lat_ms || ' milliseconds')::INTERVAL,
            jsonb_build_object(
                'rag.collection',        collections[col_idx],
                'rag.query_text',        query_texts[q_idx],
                'rag.top_k',             top_k_val,
                'rag.chunk_count',       chunk_cnt,
                'rag.similarity_scores', scores_arr,
                'rag.cache_hit',         hit_cache::TEXT
            ),
            'OK'
        ) ON CONFLICT (span_id) DO NOTHING;

        INSERT INTO rag_queries (
            query_id, tenant_id, span_id, run_id, query_text,
            retrieved_chunks, similarity_scores, latency_ms, metadata,
            context_precision, context_recall, faithfulness, answer_relevancy,
            chunk_count, collection, top_k
        ) VALUES (
            qid, 'tnt-c7ab1040eff7', sp_id, r_id, query_texts[q_idx],
            chunks_arr, scores_arr, lat_ms,
            jsonb_build_object('cache_hit', hit_cache::TEXT, 'agent', agents_rag[agent_idx]),
            prec, rec, faith, rel,
            chunk_cnt, collections[col_idx], top_k_val
        ) ON CONFLICT (query_id) DO NOTHING;

    END LOOP;
END $$;

-- ── 5. Seed drift snapshots (last 30 days, one per day per collection) ────────

DO $$
DECLARE
    collections TEXT[] := ARRAY['product_docs','support_kb','technical_specs','billing_docs','api_reference'];
    d INT;
    c INT;
    shift FLOAT;
    vol_delta FLOAT;
    prec_delta FLOAT;
    alert_lv TEXT;
    period_s TIMESTAMPTZ;
    period_e TIMESTAMPTZ;
BEGIN
    FOR d IN 0..29 LOOP
        FOR c IN 1..5 LOOP
            shift      := ROUND((0.01 + random() * 0.12)::NUMERIC, 4);
            vol_delta  := ROUND((-0.15 + random() * 0.30)::NUMERIC, 4);
            prec_delta := ROUND((-0.08 + random() * 0.16)::NUMERIC, 4);
            -- Alert when drift is significant
            alert_lv   := CASE
                WHEN shift > 0.10                        THEN 'critical'
                WHEN shift > 0.07 OR ABS(prec_delta) > 0.05 THEN 'warning'
                ELSE 'none'
            END;
            period_s := NOW() - ((d + 1) || ' days')::INTERVAL;
            period_e := NOW() - (d       || ' days')::INTERVAL;

            INSERT INTO rag_drift_snapshots (
                snapshot_id, tenant_id, collection,
                period_start, period_end,
                mean_cosine_shift, query_volume_delta, precision_delta,
                alert_level, metadata
            ) VALUES (
                'drift_' || d || '_' || c || '_' || md5((d * 10 + c)::TEXT),
                'tnt-c7ab1040eff7', collections[c],
                period_s, period_e,
                shift, vol_delta, prec_delta,
                alert_lv,
                jsonb_build_object('collection', collections[c], 'day_offset', d)
            ) ON CONFLICT (snapshot_id) DO NOTHING;
        END LOOP;
    END LOOP;
END $$;
