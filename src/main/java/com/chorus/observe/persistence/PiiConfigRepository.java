package com.chorus.observe.persistence;

import com.chorus.observe.model.PiiConfig;
import com.chorus.observe.model.PiiRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PiiConfigRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PiiConfigRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Optional<PiiConfig> findByTenantId(@NonNull String tenantId) {
        List<PiiConfig> rows = jdbc.query(
            "SELECT master_enabled, rules FROM pii_config WHERE tenant_id = ?",
            (rs, i) -> {
                try {
                    List<PiiRule> rules = mapper.readValue(
                        rs.getString("rules"), new TypeReference<>() {});
                    return new PiiConfig(rs.getBoolean("master_enabled"), rules);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to deserialize PII rules", e);
                }
            },
            tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void save(@NonNull String tenantId, @NonNull PiiConfig config) {
        try {
            String rulesJson = mapper.writeValueAsString(config.rules());
            jdbc.update("""
                INSERT INTO pii_config (tenant_id, master_enabled, rules, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (tenant_id) DO UPDATE SET
                    master_enabled = EXCLUDED.master_enabled,
                    rules          = EXCLUDED.rules,
                    updated_at     = NOW()
                """,
                tenantId, config.masterEnabled(), rulesJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize PII rules", e);
        }
    }
}
