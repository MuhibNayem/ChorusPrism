-- ============================================================
-- V21: Enforce custom tenant ID 'tnt-c7ab1040eff7' & seed provenance
-- ============================================================

-- 1. Create target tenant record if not exists
INSERT INTO tenants (tenant_id, name, config)
VALUES ('tnt-c7ab1040eff7', 'Customer Org', '{"plan":"enterprise","max_users":100}')
ON CONFLICT (tenant_id) DO NOTHING;

-- 2. Migrate retention policies
UPDATE retention_policies 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 3. Migrate runs
UPDATE runs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 4. Migrate spans (if tenant_id exists, fallback safely)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'spans' AND column_name = 'tenant_id'
    ) THEN
        UPDATE spans 
        SET tenant_id = 'tnt-c7ab1040eff7' 
        WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';
    END IF;
END $$;

-- 5. Migrate RAG queries
UPDATE rag_queries 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 6. Migrate RAG drift snapshots
UPDATE rag_drift_snapshots 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 7. Migrate provenance entries
UPDATE provenance_entries 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 8. Migrate metric snapshots
UPDATE metric_snapshots 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 9. Migrate export configurations
UPDATE export_configs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 10. Migrate audit logs
UPDATE audit_logs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 11. Migrate alert rules
UPDATE alert_rules 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 12. Migrate alert events
UPDATE alert_events 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 13. Migrate datasets
UPDATE datasets 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';



-- 15. Migrate evaluation runs
UPDATE eval_runs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 16. Migrate replay runs
UPDATE replay_runs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 17. Migrate red team runs
UPDATE red_team_runs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 18. Migrate breakpoints
UPDATE breakpoints 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 19. Migrate users
UPDATE users 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 20. Migrate SCIM tokens
UPDATE scim_tokens 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 21. Migrate SSO configurations
UPDATE tenant_oauth_configs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

UPDATE tenant_saml_configs 
SET tenant_id = 'tnt-c7ab1040eff7' 
WHERE tenant_id = 'default' OR tenant_id = 'tenant_demo';

-- 22. Seed Provenance data referencing updated runs
DO $$
DECLARE
    r RECORD;
    i INT := 1;
BEGIN
    FOR r IN (SELECT run_id, agent_id, start_time FROM runs LIMIT 50) LOOP
        INSERT INTO provenance_entries (
            entry_id, run_id, agent_id, decision_type, input_state, 
            reasoning, output, parent_ids, timestamp, metadata, tenant_id
        )
        VALUES (
            'prov_' || i || '_' || md5(r.run_id),
            r.run_id,
            r.agent_id,
            CASE WHEN i % 3 = 0 THEN 'RAG_QUERY'
                 WHEN i % 3 = 1 THEN 'TOOL_EXECUTION'
                 ELSE 'MODEL_CALL' END,
            '{"user_prompt": "Run diagnostic check for system state", "session_id": "sess_' || i || '"}',
            'Reasoning step ' || i || ': Analyzed state vectors, selected agent ' || r.agent_id || ' to resolve prompt using model and active context.',
            '{"status": "resolved", "action_taken": "analyzed logs", "confidence": ' || ROUND((0.8 + random() * 0.19)::NUMERIC, 2)::TEXT || '}',
            '[]',
            r.start_time,
            '{"latency_ms": 120}',
            'tnt-c7ab1040eff7'
        ) ON CONFLICT (entry_id) DO NOTHING;
        i := i + 1;
    END LOOP;
END $$;

-- 23. Seed User and Admin Role for custom tenant tnt-c7ab1040eff7
INSERT INTO users (user_id, tenant_id, email, password_hash, display_name, status, last_login_at, auth_source, created_at, updated_at)
VALUES (
    'usr-nayem-admin', 
    'tnt-c7ab1040eff7', 
    'nayem.drmc@gmail.com', 
    '$2b$12$jXHafDCampWBD4oGfHIlTOheEWK6HVRUJ/w8K/HqeE4qy9fbCnd6y', 
    'Nayem', 
    'ACTIVE', 
    NULL, 
    'LOCAL', 
    NOW(), 
    NOW()
) ON CONFLICT (user_id) DO NOTHING;

INSERT INTO roles (role_id, tenant_id, name, permissions, description, created_at, updated_at)
VALUES (
    'role-admin-tnt-c7ab1040eff7',
    'tnt-c7ab1040eff7',
    'Administrator',
    '["runs:read", "runs:write", "spans:read", "evals:read", "evals:write", "alerts:read", "alerts:write", "dashboards:read", "dashboards:write", "users:read", "users:write", "settings:read", "settings:write", "audit:read", "export:read", "export:write", "admin"]'::jsonb,
    'Full administrative access',
    NOW(),
    NOW()
) ON CONFLICT (role_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id, created_at)
VALUES (
    'usr-nayem-admin',
    'role-admin-tnt-c7ab1040eff7',
    NOW()
) ON CONFLICT (user_id, role_id) DO NOTHING;
