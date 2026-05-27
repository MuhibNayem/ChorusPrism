-- Enterprise SSO upgrade: persistent SP key, role/domain/attribute mappings, metadata XML upload

-- Persistent SP signing key per tenant (one key pair per tenant, shared across providers)
CREATE TABLE sso_sp_keys (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    private_key_encrypted   TEXT NOT NULL,
    cert_pem                TEXT NOT NULL,
    algorithm               VARCHAR(16) NOT NULL DEFAULT 'RSA',
    key_size_bits           INTEGER NOT NULL DEFAULT 2048,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id)
);

CREATE INDEX idx_sso_sp_keys_tenant ON sso_sp_keys(tenant_id);

-- SAML config enhancements
ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS role_mappings      JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS allowed_domains    JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS attribute_mappings JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS idp_cert_pem       TEXT;
ALTER TABLE tenant_saml_configs ADD COLUMN IF NOT EXISTS idp_metadata_xml   TEXT;

-- OIDC config enhancements
ALTER TABLE tenant_oauth_configs ADD COLUMN IF NOT EXISTS role_mappings      JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE tenant_oauth_configs ADD COLUMN IF NOT EXISTS allowed_domains    JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE tenant_oauth_configs ADD COLUMN IF NOT EXISTS attribute_mappings JSONB NOT NULL DEFAULT '{}'::jsonb;
