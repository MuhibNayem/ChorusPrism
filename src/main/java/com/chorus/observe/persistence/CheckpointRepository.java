package com.chorus.observe.persistence;

import com.chorus.observe.model.Checkpoint;
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
 * JDBC repository for checkpoints.
 */
public class CheckpointRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Checkpoint> rowMapper;

    public CheckpointRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new CheckpointRowMapper(mapper);
    }

    public void save(@NonNull Checkpoint checkpoint) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO checkpoints (checkpoint_id, tenant_id, run_id, sequence, state_snapshot, next_nodes, metadata, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (run_id, sequence) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                checkpoint_id = EXCLUDED.checkpoint_id,
                state_snapshot = EXCLUDED.state_snapshot,
                next_nodes = EXCLUDED.next_nodes,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            checkpoint.checkpointId(), tenantId != null ? tenantId : "default", checkpoint.runId(), checkpoint.sequence(),
            toJson(checkpoint.stateSnapshot()), toJson(checkpoint.nextNodes()),
            toJson(checkpoint.metadata()), Timestamp.from(checkpoint.createdAt())
        );
    }

    public @NonNull Optional<Checkpoint> findById(@NonNull String checkpointId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM checkpoints WHERE checkpoint_id = ? AND tenant_id = ?", rowMapper, checkpointId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM checkpoints WHERE checkpoint_id = ?", rowMapper, checkpointId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Checkpoint> findByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM checkpoints WHERE run_id = ? AND tenant_id = ? ORDER BY sequence", rowMapper, runId, tenantId);
        }
        return jdbc.query("SELECT * FROM checkpoints WHERE run_id = ? ORDER BY sequence", rowMapper, runId);
    }

    public @NonNull List<Checkpoint> findByRunId(@NonNull String runId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM checkpoints WHERE run_id = ? AND tenant_id = ? ORDER BY sequence LIMIT ? OFFSET ?", rowMapper, runId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM checkpoints WHERE run_id = ? ORDER BY sequence LIMIT ? OFFSET ?", rowMapper, runId, limit, offset);
    }

    public long countByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM checkpoints WHERE run_id = ? AND tenant_id = ?", Long.class, runId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM checkpoints WHERE run_id = ?", Long.class, runId);
        return count != null ? count : 0L;
    }

    public @NonNull Optional<Checkpoint> findByRunIdAndSequence(@NonNull String runId, int sequence) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM checkpoints WHERE run_id = ? AND sequence = ? AND tenant_id = ?", rowMapper, runId, sequence, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM checkpoints WHERE run_id = ? AND sequence = ?", rowMapper, runId, sequence));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull Optional<Checkpoint> findLatestByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM checkpoints WHERE run_id = ? AND tenant_id = ? ORDER BY sequence DESC LIMIT 1", rowMapper, runId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM checkpoints WHERE run_id = ? ORDER BY sequence DESC LIMIT 1", rowMapper, runId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void deleteByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM checkpoints WHERE run_id = ? AND tenant_id = ?", runId, tenantId);
        } else {
            jdbc.update("DELETE FROM checkpoints WHERE run_id = ?", runId);
        }
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class CheckpointRowMapper implements RowMapper<Checkpoint> {
        private final ObjectMapper mapper;

        CheckpointRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Checkpoint mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Checkpoint(
                    rs.getString("checkpoint_id"),
                    rs.getString("run_id"),
                    rs.getInt("sequence"),
                    mapper.readValue(rs.getString("state_snapshot"), new TypeReference<Map<String, Object>>() {}),
                    mapper.readValue(rs.getString("next_nodes"), new TypeReference<List<String>>() {}),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
