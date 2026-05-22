package com.chorus.observe.persistence;

import com.chorus.observe.model.EvalRun;
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
 * JDBC repository for evaluation runs.
 */
public class EvalRunRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<EvalRun> rowMapper;

    public EvalRunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new EvalRunRowMapper(mapper);
    }

    public void save(@NonNull EvalRun evalRun) {
        String sql = """
            INSERT INTO eval_runs (eval_run_id, dataset_id, name, agent_config, scorer_config, parallelism, status, summary_metrics, started_at, finished_at, created_at)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (eval_run_id) DO UPDATE SET
                dataset_id = EXCLUDED.dataset_id,
                name = EXCLUDED.name,
                agent_config = EXCLUDED.agent_config,
                scorer_config = EXCLUDED.scorer_config,
                parallelism = EXCLUDED.parallelism,
                status = EXCLUDED.status,
                summary_metrics = EXCLUDED.summary_metrics,
                started_at = EXCLUDED.started_at,
                finished_at = EXCLUDED.finished_at
            """;
        jdbc.update(sql,
            evalRun.evalRunId(), evalRun.datasetId(), evalRun.name(),
            toJson(evalRun.agentConfig()), toJson(evalRun.scorerConfig()),
            evalRun.parallelism(), evalRun.status().name(),
            toJson(evalRun.summaryMetrics()),
            evalRun.startedAt() != null ? Timestamp.from(evalRun.startedAt()) : null,
            evalRun.finishedAt() != null ? Timestamp.from(evalRun.finishedAt()) : null,
            Timestamp.from(evalRun.createdAt())
        );
    }

    public @NonNull Optional<EvalRun> findById(@NonNull String evalRunId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM eval_runs WHERE eval_run_id = ?", rowMapper, evalRunId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<EvalRun> findByDatasetId(@NonNull String datasetId) {
        return jdbc.query("SELECT * FROM eval_runs WHERE dataset_id = ? ORDER BY created_at DESC", rowMapper, datasetId);
    }

    public @NonNull List<EvalRun> findByDatasetId(@NonNull String datasetId, int limit, int offset) {
        return jdbc.query("SELECT * FROM eval_runs WHERE dataset_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, datasetId, limit, offset);
    }

    public long countByDatasetId(@NonNull String datasetId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_runs WHERE dataset_id = ?", Long.class, datasetId);
        return count != null ? count : 0L;
    }

    public @NonNull List<EvalRun> findAll() {
        return jdbc.query("SELECT * FROM eval_runs ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<EvalRun> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM eval_runs ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_runs", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<EvalRun> findByStatus(EvalRun.Status status) {
        return jdbc.query("SELECT * FROM eval_runs WHERE status = ? ORDER BY created_at DESC", rowMapper, status.name());
    }

    public @NonNull List<EvalRun> findByStatus(EvalRun.Status status, int limit, int offset) {
        return jdbc.query("SELECT * FROM eval_runs WHERE status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, status.name(), limit, offset);
    }

    public long countByStatus(EvalRun.Status status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_runs WHERE status = ?", Long.class, status.name());
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String evalRunId) {
        jdbc.update("DELETE FROM eval_runs WHERE eval_run_id = ?", evalRunId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class EvalRunRowMapper implements RowMapper<EvalRun> {
        private final ObjectMapper mapper;

        EvalRunRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public EvalRun mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new EvalRun(
                    rs.getString("eval_run_id"),
                    rs.getString("dataset_id"),
                    rs.getString("name"),
                    mapper.readValue(rs.getString("agent_config"), new TypeReference<Map<String, Object>>() {}),
                    mapper.readValue(rs.getString("scorer_config"), new TypeReference<Map<String, Object>>() {}),
                    rs.getInt("parallelism"),
                    EvalRun.Status.valueOf(rs.getString("status")),
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
