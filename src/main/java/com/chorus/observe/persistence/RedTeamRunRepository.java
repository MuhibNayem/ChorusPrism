package com.chorus.observe.persistence;

import com.chorus.observe.model.RedTeamRun;
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
 * JDBC repository for red team runs.
 */
public class RedTeamRunRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<RedTeamRun> rowMapper;

    public RedTeamRunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RedTeamRunRowMapper(mapper);
    }

    public void save(@NonNull RedTeamRun run) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO red_team_runs (red_team_run_id, tenant_id, agent_config, status, total_scenarios, bypassed_count, blocked_count, summary_metrics, started_at, finished_at, created_at)
            VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (red_team_run_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                agent_config = EXCLUDED.agent_config,
                status = EXCLUDED.status,
                total_scenarios = EXCLUDED.total_scenarios,
                bypassed_count = EXCLUDED.bypassed_count,
                blocked_count = EXCLUDED.blocked_count,
                summary_metrics = EXCLUDED.summary_metrics,
                started_at = EXCLUDED.started_at,
                finished_at = EXCLUDED.finished_at
            """;
        jdbc.update(sql,
            run.redTeamRunId(), tenantId != null ? tenantId : "default", toJson(run.agentConfig()), run.status().name(),
            run.totalScenarios(), run.bypassedCount(), run.blockedCount(),
            toJson(run.summaryMetrics()),
            run.startedAt() != null ? Timestamp.from(run.startedAt()) : null,
            run.finishedAt() != null ? Timestamp.from(run.finishedAt()) : null,
            Timestamp.from(run.createdAt())
        );
    }

    public @NonNull Optional<RedTeamRun> findById(@NonNull String redTeamRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM red_team_runs WHERE red_team_run_id = ? AND tenant_id = ?", rowMapper, redTeamRunId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM red_team_runs WHERE red_team_run_id = ?", rowMapper, redTeamRunId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<RedTeamRun> findAll() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM red_team_runs WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
        }
        return jdbc.query("SELECT * FROM red_team_runs ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<RedTeamRun> findAll(int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM red_team_runs WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM red_team_runs ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_runs WHERE tenant_id = ?", Long.class, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_runs", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<RedTeamRun> findByStatus(RedTeamRun.Status status) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM red_team_runs WHERE status = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, status.name(), tenantId);
        }
        return jdbc.query("SELECT * FROM red_team_runs WHERE status = ? ORDER BY created_at DESC", rowMapper, status.name());
    }

    public @NonNull List<RedTeamRun> findByStatus(RedTeamRun.Status status, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM red_team_runs WHERE status = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, status.name(), tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM red_team_runs WHERE status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, status.name(), limit, offset);
    }

    public long countByStatus(RedTeamRun.Status status) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_runs WHERE status = ? AND tenant_id = ?", Long.class, status.name(), tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_runs WHERE status = ?", Long.class, status.name());
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class RedTeamRunRowMapper implements RowMapper<RedTeamRun> {
        private final ObjectMapper mapper;

        RedTeamRunRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public RedTeamRun mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new RedTeamRun(
                    rs.getString("red_team_run_id"),
                    mapper.readValue(rs.getString("agent_config"), new TypeReference<Map<String, Object>>() {}),
                    RedTeamRun.Status.valueOf(rs.getString("status")),
                    rs.getInt("total_scenarios"),
                    rs.getInt("bypassed_count"),
                    rs.getInt("blocked_count"),
                    rs.getInt("progress_percent"),
                    mapper.readValue(rs.getString("summary_metrics"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                    rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null,
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
