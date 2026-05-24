package com.chorus.observe.persistence;

import com.chorus.observe.model.TenantOauthConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class TenantOauthConfigRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<TenantOauthConfig> rowMapper;

    public TenantOauthConfigRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new TenantOauthConfigRowMapper(mapper);
    }

    public void save(@NonNull TenantOauthConfig config) {
        String sql = """
            INSERT INTO tenant_oauth_configs (id, tenant_id, provider_name, client_id, client_secret, issuer_uri, scopes, default_role, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, provider_name) DO UPDATE SET
                client_id = EXCLUDED.client_id,
                client_secret = EXCLUDED.client_secret,
                issuer_uri = EXCLUDED.issuer_uri,
                scopes = EXCLUDED.scopes,
                default_role = EXCLUDED.default_role,
                enabled = EXCLUDED.enabled,
                updated_at = EXCLUDED.updated_at
            """;
        try {
            jdbc.update(sql,
                config.id() != null ? config.id() : UUID.randomUUID(),
                config.tenantId(), config.providerName(), config.clientId(),
                config.clientSecret(), config.issuerUri(),
                mapper.writeValueAsString(config.scopes()),
                config.defaultRole(), config.enabled(),
                Timestamp.from(config.createdAt()), Timestamp.from(config.updatedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize scopes", e);
        }
    }

    public @NonNull Optional<TenantOauthConfig> findById(@NonNull UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM tenant_oauth_configs WHERE id = ?", rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<TenantOauthConfig> findByTenantId(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM tenant_oauth_configs WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public @NonNull Optional<TenantOauthConfig> findByTenantIdAndProviderName(@NonNull String tenantId, @NonNull String providerName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM tenant_oauth_configs WHERE tenant_id = ? AND provider_name = ?", rowMapper, tenantId, providerName));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<TenantOauthConfig> findEnabledByTenantId(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM tenant_oauth_configs WHERE tenant_id = ? AND enabled = true ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public void deleteById(@NonNull UUID id) {
        jdbc.update("DELETE FROM tenant_oauth_configs WHERE id = ?", id);
    }

    private static final class TenantOauthConfigRowMapper implements RowMapper<TenantOauthConfig> {
        private final ObjectMapper mapper;

        TenantOauthConfigRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public TenantOauthConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new TenantOauthConfig(
                    rs.getObject("id", UUID.class),
                    rs.getString("tenant_id"),
                    rs.getString("provider_name"),
                    rs.getString("client_id"),
                    rs.getString("client_secret"),
                    rs.getString("issuer_uri"),
                    mapper.readValue(rs.getString("scopes"), new TypeReference<List<String>>() {}),
                    rs.getString("default_role"),
                    rs.getBoolean("enabled"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
