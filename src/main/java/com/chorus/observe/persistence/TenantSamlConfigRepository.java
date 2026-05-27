package com.chorus.observe.persistence;

import com.chorus.observe.model.RoleMapping;
import com.chorus.observe.model.TenantSamlConfig;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TenantSamlConfigRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<TenantSamlConfig> rowMapper;

    public TenantSamlConfigRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.mapper = mapper;
        this.rowMapper = new TenantSamlConfigRowMapper(mapper);
    }

    public void save(@NonNull TenantSamlConfig config) {
        try {
            String sql = """
                INSERT INTO tenant_saml_configs (
                    id, tenant_id, provider_name, entity_id, sign_on_url, signing_cert_thumbprint,
                    metadata_url, acs_url, default_role, enabled,
                    role_mappings, allowed_domains, attribute_mappings, idp_cert_pem, idp_metadata_xml,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, provider_name) DO UPDATE SET
                    entity_id               = EXCLUDED.entity_id,
                    sign_on_url             = EXCLUDED.sign_on_url,
                    signing_cert_thumbprint = EXCLUDED.signing_cert_thumbprint,
                    metadata_url            = EXCLUDED.metadata_url,
                    acs_url                 = EXCLUDED.acs_url,
                    default_role            = EXCLUDED.default_role,
                    enabled                 = EXCLUDED.enabled,
                    role_mappings           = EXCLUDED.role_mappings,
                    allowed_domains         = EXCLUDED.allowed_domains,
                    attribute_mappings      = EXCLUDED.attribute_mappings,
                    idp_cert_pem            = EXCLUDED.idp_cert_pem,
                    idp_metadata_xml        = EXCLUDED.idp_metadata_xml,
                    updated_at              = EXCLUDED.updated_at
                """;
            jdbc.update(sql,
                config.id() != null ? config.id() : UUID.randomUUID(),
                config.tenantId(), config.providerName(), config.entityId(),
                config.signOnUrl(), config.signingCertThumbprint(),
                config.metadataUrl(), config.acsUrl(),
                config.defaultRole(), config.enabled(),
                mapper.writeValueAsString(config.roleMappings()),
                mapper.writeValueAsString(config.allowedDomains()),
                mapper.writeValueAsString(config.attributeMappings()),
                config.idpCertPem(), config.idpMetadataXml(),
                Timestamp.from(config.createdAt()), Timestamp.from(config.updatedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSONB fields", e);
        }
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
        return jdbc.query(
            "SELECT * FROM tenant_saml_configs WHERE tenant_id = ? ORDER BY created_at DESC",
            rowMapper, tenantId);
    }

    public @NonNull Optional<TenantSamlConfig> findByTenantIdAndProviderName(@NonNull String tenantId,
                                                                               @NonNull String providerName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM tenant_saml_configs WHERE tenant_id = ? AND provider_name = ?",
                rowMapper, tenantId, providerName));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<TenantSamlConfig> findEnabledByTenantId(@NonNull String tenantId) {
        return jdbc.query(
            "SELECT * FROM tenant_saml_configs WHERE tenant_id = ? AND enabled = true ORDER BY created_at DESC",
            rowMapper, tenantId);
    }

    public void deleteById(@NonNull UUID id) {
        jdbc.update("DELETE FROM tenant_saml_configs WHERE id = ?", id);
    }

    private static final class TenantSamlConfigRowMapper implements RowMapper<TenantSamlConfig> {
        private final ObjectMapper mapper;

        TenantSamlConfigRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public TenantSamlConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
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
                    parseRoleMappings(rs.getString("role_mappings")),
                    parseList(rs.getString("allowed_domains")),
                    parseMap(rs.getString("attribute_mappings")),
                    rs.getString("idp_cert_pem"),
                    rs.getString("idp_metadata_xml"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSONB fields", e);
            }
        }

        private List<RoleMapping> parseRoleMappings(String json) throws JsonProcessingException {
            if (json == null || json.isBlank()) return List.of();
            return mapper.readValue(json, new TypeReference<>() {});
        }

        private List<String> parseList(String json) throws JsonProcessingException {
            if (json == null || json.isBlank()) return List.of();
            return mapper.readValue(json, new TypeReference<>() {});
        }

        private Map<String, String> parseMap(String json) throws JsonProcessingException {
            if (json == null || json.isBlank()) return Map.of();
            return mapper.readValue(json, new TypeReference<>() {});
        }
    }
}
