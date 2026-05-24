package com.chorus.observe.persistence;

import com.chorus.observe.model.TenantSamlConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class TenantSamlConfigRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<TenantSamlConfig> rowMapper = new TenantSamlConfigRowMapper();

    public TenantSamlConfigRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull TenantSamlConfig config) {
        String sql = """
            INSERT INTO tenant_saml_configs (id, tenant_id, provider_name, entity_id, sign_on_url, signing_cert_thumbprint, metadata_url, acs_url, default_role, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, provider_name) DO UPDATE SET
                entity_id = EXCLUDED.entity_id,
                sign_on_url = EXCLUDED.sign_on_url,
                signing_cert_thumbprint = EXCLUDED.signing_cert_thumbprint,
                metadata_url = EXCLUDED.metadata_url,
                acs_url = EXCLUDED.acs_url,
                default_role = EXCLUDED.default_role,
                enabled = EXCLUDED.enabled,
                updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            config.id() != null ? config.id() : UUID.randomUUID(),
            config.tenantId(), config.providerName(), config.entityId(),
            config.signOnUrl(), config.signingCertThumbprint(),
            config.metadataUrl(), config.acsUrl(),
            config.defaultRole(), config.enabled(),
            Timestamp.from(config.createdAt()), Timestamp.from(config.updatedAt()));
    }

    public @NonNull Optional<TenantSamlConfig> findById(@NonNull UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM tenant_saml_configs WHERE id = ?", rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<TenantSamlConfig> findByTenantId(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM tenant_saml_configs WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public @NonNull Optional<TenantSamlConfig> findByTenantIdAndProviderName(@NonNull String tenantId, @NonNull String providerName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM tenant_saml_configs WHERE tenant_id = ? AND provider_name = ?", rowMapper, tenantId, providerName));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<TenantSamlConfig> findEnabledByTenantId(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM tenant_saml_configs WHERE tenant_id = ? AND enabled = true ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public void deleteById(@NonNull UUID id) {
        jdbc.update("DELETE FROM tenant_saml_configs WHERE id = ?", id);
    }

    private static final class TenantSamlConfigRowMapper implements RowMapper<TenantSamlConfig> {
        @Override
        public TenantSamlConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TenantSamlConfig(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("provider_name"),
                rs.getString("entity_id"),
                rs.getString("sign_on_url"),
                rs.getString("signing_cert_thumbprint"),
                rs.getString("metadata_url"),
                rs.getString("acs_url"),
                rs.getString("default_role"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
