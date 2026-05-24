package com.chorus.observe.persistence;

import com.chorus.observe.model.GuardrailTelemetry;
import com.chorus.observe.security.TenantContext;
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
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository for guardrail telemetry events.
 */
public class GuardrailTelemetryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<GuardrailTelemetry> rowMapper;

    public GuardrailTelemetryRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new GuardrailTelemetryRowMapper(mapper);
    }

    public void save(@NonNull GuardrailTelemetry telemetry) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO guardrail_telemetry (telemetry_id, tenant_id, run_id, guardrail_name, tier, action, confidence, latency_ms, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (telemetry_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                run_id = EXCLUDED.run_id,
                guardrail_name = EXCLUDED.guardrail_name,
                tier = EXCLUDED.tier,
                action = EXCLUDED.action,
                confidence = EXCLUDED.confidence,
                latency_ms = EXCLUDED.latency_ms,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            telemetry.telemetryId(), tenantId != null ? tenantId : "default", telemetry.runId(), telemetry.guardrailName(),
            telemetry.tier(), telemetry.action(), telemetry.confidence(),
            telemetry.latencyMs(), toJson(telemetry.metadata()),
            Timestamp.from(telemetry.createdAt())
        );
    }

    public @NonNull Optional<GuardrailTelemetry> findById(@NonNull String telemetryId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM guardrail_telemetry WHERE telemetry_id = ? AND tenant_id = ?", rowMapper, telemetryId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM guardrail_telemetry WHERE telemetry_id = ?", rowMapper, telemetryId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<GuardrailTelemetry> findByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM guardrail_telemetry WHERE run_id = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, runId, tenantId);
        }
        return jdbc.query("SELECT * FROM guardrail_telemetry WHERE run_id = ? ORDER BY created_at DESC", rowMapper, runId);
    }

    public @NonNull List<GuardrailTelemetry> findByRunId(@NonNull String runId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM guardrail_telemetry WHERE run_id = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, runId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM guardrail_telemetry WHERE run_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, runId, limit, offset);
    }

    public long countByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM guardrail_telemetry WHERE run_id = ? AND tenant_id = ?", Long.class, runId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM guardrail_telemetry WHERE run_id = ?", Long.class, runId);
        return count != null ? count : 0L;
    }

    public @NonNull List<GuardrailTelemetry> findByGuardrailName(@NonNull String guardrailName) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM guardrail_telemetry WHERE guardrail_name = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, guardrailName, tenantId);
        }
        return jdbc.query("SELECT * FROM guardrail_telemetry WHERE guardrail_name = ? ORDER BY created_at DESC", rowMapper, guardrailName);
    }

    public @NonNull List<GuardrailTelemetry> findByGuardrailName(@NonNull String guardrailName, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM guardrail_telemetry WHERE guardrail_name = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, guardrailName, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM guardrail_telemetry WHERE guardrail_name = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, guardrailName, limit, offset);
    }

    public long countByGuardrailName(@NonNull String guardrailName) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM guardrail_telemetry WHERE guardrail_name = ? AND tenant_id = ?", Long.class, guardrailName, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM guardrail_telemetry WHERE guardrail_name = ?", Long.class, guardrailName);
        return count != null ? count : 0L;
    }

    public @NonNull List<GuardrailTelemetry> findRecent(int limit) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM guardrail_telemetry WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?", rowMapper, tenantId, limit);
        }
        return jdbc.query("SELECT * FROM guardrail_telemetry ORDER BY created_at DESC LIMIT ?", rowMapper, limit);
    }

    public @NonNull List<GuardrailTelemetry> findRecent(int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM guardrail_telemetry WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM guardrail_telemetry ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM guardrail_telemetry WHERE tenant_id = ?", Long.class, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM guardrail_telemetry", Long.class);
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class GuardrailTelemetryRowMapper implements RowMapper<GuardrailTelemetry> {
        private final ObjectMapper mapper;

        GuardrailTelemetryRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public GuardrailTelemetry mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new GuardrailTelemetry(
                    rs.getString("telemetry_id"),
                    rs.getString("run_id"),
                    rs.getString("guardrail_name"),
                    rs.getInt("tier"),
                    rs.getString("action"),
                    rs.getObject("confidence", Double.class),
                    rs.getLong("latency_ms"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
