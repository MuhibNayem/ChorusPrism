-- OAuth2/OIDC IdP configurations per tenant (multiple IdPs supported)
CREATE TABLE tenant_oauth_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    provider_name   VARCHAR(64) NOT NULL,
    client_id       VARCHAR(256) NOT NULL,
    client_secret   VARCHAR(512) NOT NULL,
    issuer_uri      VARCHAR(512) NOT NULL,
    scopes          JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_role    VARCHAR(64) NOT NULL DEFAULT 'VIEWER',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, provider_name)
);

CREATE INDEX idx_tenant_oauth_configs_tenant ON tenant_oauth_configs(tenant_id);

-- SAML IdP configurations per tenant (multiple IdPs supported)
CREATE TABLE tenant_saml_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    provider_name   VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(512) NOT NULL,
    sign_on_url     VARCHAR(512) NOT NULL,
    signing_cert_thumbprint VARCHAR(128) NOT NULL,
    metadata_url    VARCHAR(512),
    acs_url         VARCHAR(512) NOT NULL,
    default_role    VARCHAR(64) NOT NULL DEFAULT 'VIEWER',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, provider_name)
);

CREATE INDEX idx_tenant_saml_configs_tenant ON tenant_saml_configs(tenant_id);

-- SCIM provisioning tokens (separate from api_keys)
CREATE TABLE scim_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    token_hash      VARCHAR(256) NOT NULL UNIQUE,
    scopes          JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_scim_tokens_tenant ON scim_tokens(tenant_id);
CREATE INDEX idx_scim_tokens_hash ON scim_tokens(token_hash);

-- Add auth_source to users for tracking login method
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_source VARCHAR(16) NOT NULL DEFAULT 'LOCAL';

-- LOWER(email) unique constraint per tenant for duplicate prevention (SCIM-07)
CREATE UNIQUE INDEX idx_users_tenant_lower_email ON users(tenant_id, LOWER(email));
