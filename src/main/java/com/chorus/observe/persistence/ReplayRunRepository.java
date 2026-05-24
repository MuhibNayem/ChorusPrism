package com.chorus.observe.persistence;

import com.chorus.observe.model.ReplayRun;
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
 * JDBC repository for replay runs.
 */
public class ReplayRunRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<ReplayRun> rowMapper;

    public ReplayRunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new ReplayRunRowMapper(mapper);
    }

    public void save(@NonNull ReplayRun replayRun) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO replay_runs (replay_run_id, tenant_id, original_run_id, from_checkpoint_id, state_overrides, status, started_at, finished_at, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (replay_run_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                original_run_id = EXCLUDED.original_run_id,
                from_checkpoint_id = EXCLUDED.from_checkpoint_id,
                state_overrides = EXCLUDED.state_overrides,
                status = EXCLUDED.status,
                started_at = EXCLUDED.started_at,
                finished_at = EXCLUDED.finished_at
            """;
        jdbc.update(sql,
            replayRun.replayRunId(), tenantId != null ? tenantId : "default", replayRun.originalRunId(), replayRun.fromCheckpointId(),
            toJson(replayRun.stateOverrides()), replayRun.status().name(),
            replayRun.startedAt() != null ? Timestamp.from(replayRun.startedAt()) : null,
            replayRun.finishedAt() != null ? Timestamp.from(replayRun.finishedAt()) : null,
            Timestamp.from(replayRun.createdAt())
        );
    }

    public @NonNull Optional<ReplayRun> findById(@NonNull String replayRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM replay_runs WHERE replay_run_id = ? AND tenant_id = ?", rowMapper, replayRunId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM replay_runs WHERE replay_run_id = ?", rowMapper, replayRunId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<ReplayRun> findByOriginalRunId(@NonNull String originalRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM replay_runs WHERE original_run_id = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, originalRunId, tenantId);
        }
        return jdbc.query("SELECT * FROM replay_runs WHERE original_run_id = ? ORDER BY created_at DESC", rowMapper, originalRunId);
    }

    public @NonNull List<ReplayRun> findByOriginalRunId(@NonNull String originalRunId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM replay_runs WHERE original_run_id = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, originalRunId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM replay_runs WHERE original_run_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, originalRunId, limit, offset);
    }

    public long countByOriginalRunId(@NonNull String originalRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM replay_runs WHERE original_run_id = ? AND tenant_id = ?", Long.class, originalRunId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM replay_runs WHERE original_run_id = ?", Long.class, originalRunId);
        return count != null ? count : 0L;
    }

    public @NonNull List<ReplayRun> findAll() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM replay_runs WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
        }
        return jdbc.query("SELECT * FROM replay_runs ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<ReplayRun> findAll(int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM replay_runs WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM replay_runs ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM replay_runs WHERE tenant_id = ?", Long.class, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM replay_runs", Long.class);
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class ReplayRunRowMapper implements RowMapper<ReplayRun> {
        private final ObjectMapper mapper;

        ReplayRunRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public ReplayRun mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new ReplayRun(
                    rs.getString("replay_run_id"),
                    rs.getString("original_run_id"),
                    rs.getString("from_checkpoint_id"),
                    mapper.readValue(rs.getString("state_overrides"), new TypeReference<Map<String, Object>>() {}),
                    ReplayRun.Status.valueOf(rs.getString("status")),
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
