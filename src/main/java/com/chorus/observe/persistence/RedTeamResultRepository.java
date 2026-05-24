package com.chorus.observe.persistence;

import com.chorus.observe.model.RedTeamResult;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.model.RedTeamScenario;
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
 * JDBC repository for red team results.
 */
public class RedTeamResultRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<RedTeamResult> rowMapper;

    public RedTeamResultRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RedTeamResultRowMapper(mapper);
    }

    public void save(@NonNull RedTeamResult result) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO red_team_results (result_id, tenant_id, red_team_run_id, scenario_id, agent_output, guardrail_result, bypassed, severity, latency_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (result_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                agent_output = EXCLUDED.agent_output,
                guardrail_result = EXCLUDED.guardrail_result,
                bypassed = EXCLUDED.bypassed,
                severity = EXCLUDED.severity,
                latency_ms = EXCLUDED.latency_ms
            """;
        jdbc.update(sql,
            result.resultId(), tenantId != null ? tenantId : "default", result.redTeamRunId(), result.scenarioId(),
            result.agentOutput(), toJson(result.guardrailResult()),
            result.bypassed(), result.severity().name(), result.latencyMs(),
            Timestamp.from(result.createdAt())
        );
    }

    public @NonNull Optional<RedTeamResult> findById(@NonNull String resultId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM red_team_results WHERE result_id = ? AND tenant_id = ?", rowMapper, resultId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM red_team_results WHERE result_id = ?", rowMapper, resultId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<RedTeamResult> findByRedTeamRunId(@NonNull String redTeamRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM red_team_results WHERE red_team_run_id = ? AND tenant_id = ? ORDER BY created_at", rowMapper, redTeamRunId, tenantId);
        }
        return jdbc.query("SELECT * FROM red_team_results WHERE red_team_run_id = ? ORDER BY created_at", rowMapper, redTeamRunId);
    }

    public @NonNull List<RedTeamResult> findByRedTeamRunId(@NonNull String redTeamRunId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM red_team_results WHERE red_team_run_id = ? AND tenant_id = ? ORDER BY created_at LIMIT ? OFFSET ?", rowMapper, redTeamRunId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM red_team_results WHERE red_team_run_id = ? ORDER BY created_at LIMIT ? OFFSET ?", rowMapper, redTeamRunId, limit, offset);
    }

    public long countByRedTeamRunId(@NonNull String redTeamRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_results WHERE red_team_run_id = ? AND tenant_id = ?", Long.class, redTeamRunId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_results WHERE red_team_run_id = ?", Long.class, redTeamRunId);
        return count != null ? count : 0L;
    }

    public long countBypassedByRunId(@NonNull String redTeamRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_results WHERE red_team_run_id = ? AND bypassed = TRUE AND tenant_id = ?", Long.class, redTeamRunId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_results WHERE red_team_run_id = ? AND bypassed = TRUE", Long.class, redTeamRunId);
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class RedTeamResultRowMapper implements RowMapper<RedTeamResult> {
        private final ObjectMapper mapper;

        RedTeamResultRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public RedTeamResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new RedTeamResult(
                    rs.getString("result_id"),
                    rs.getString("red_team_run_id"),
                    rs.getString("scenario_id"),
                    rs.getString("agent_output"),
                    mapper.readValue(rs.getString("guardrail_result"), new TypeReference<Map<String, Object>>() {}),
                    rs.getBoolean("bypassed"),
                    RedTeamScenario.Severity.valueOf(rs.getString("severity")),
                    rs.getLong("latency_ms"),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
