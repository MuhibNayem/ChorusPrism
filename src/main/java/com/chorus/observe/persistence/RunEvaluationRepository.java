package com.chorus.observe.persistence;

import com.chorus.observe.model.RunEvaluation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC repository for run evaluations.
 */
public class RunEvaluationRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<RunEvaluation> rowMapper;

    public RunEvaluationRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RunEvaluationRowMapper(mapper);
    }

    public void save(@NonNull RunEvaluation evaluation) {
        String sql = """
            INSERT INTO run_evaluations (evaluation_id, run_id, evaluator_id, score, passed, details)
            VALUES (?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (evaluation_id) DO UPDATE SET
                run_id = EXCLUDED.run_id,
                evaluator_id = EXCLUDED.evaluator_id,
                score = EXCLUDED.score,
                passed = EXCLUDED.passed,
                details = EXCLUDED.details
            """;
        jdbc.update(sql,
            evaluation.evaluationId(), evaluation.runId(), evaluation.evaluatorId(),
            evaluation.score(), evaluation.passed(),
            evaluation.details() != null ? toJson(evaluation.details()) : "{}"
        );
    }

    public @NonNull List<RunEvaluation> findByRunId(@NonNull String runId) {
        return jdbc.query(
            "SELECT * FROM run_evaluations WHERE run_id = ? ORDER BY created_at DESC",
            rowMapper, runId);
    }

    public @NonNull List<RunEvaluation> findByRunIds(@NonNull List<String> runIds) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(runIds.size(), "?"));
        return jdbc.query(
            "SELECT * FROM run_evaluations WHERE run_id IN (" + placeholders + ") ORDER BY created_at DESC",
            rowMapper, runIds.toArray());
    }

    public @NonNull List<RunEvaluation> findByEvaluatorId(@NonNull String evaluatorId) {
        return jdbc.query(
            "SELECT * FROM run_evaluations WHERE evaluator_id = ? ORDER BY created_at DESC",
            rowMapper, evaluatorId);
    }

    public long countByEvaluatorId(@NonNull String evaluatorId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM run_evaluations WHERE evaluator_id = ?", Long.class, evaluatorId);
        return count != null ? count : 0L;
    }

    public double avgScoreByEvaluatorId(@NonNull String evaluatorId) {
        Double avg = jdbc.queryForObject(
            "SELECT AVG(score) FROM run_evaluations WHERE evaluator_id = ?", Double.class, evaluatorId);
        return avg != null ? avg : 0.0;
    }

    public double avgScoreByEvaluatorIdLast24h(@NonNull String evaluatorId) {
        Double avg = jdbc.queryForObject(
            "SELECT AVG(score) FROM run_evaluations WHERE evaluator_id = ? AND created_at >= NOW() - INTERVAL '24 hours'",
            Double.class, evaluatorId);
        return avg != null ? avg : 0.0;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class RunEvaluationRowMapper implements RowMapper<RunEvaluation> {
        private final ObjectMapper mapper;

        RunEvaluationRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public RunEvaluation mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new RunEvaluation(
                    rs.getString("evaluation_id"),
                    rs.getString("run_id"),
                    rs.getString("evaluator_id"),
                    rs.getDouble("score"),
                    rs.getBoolean("passed"),
                    mapper.readValue(rs.getString("details"), new TypeReference<Map<String, Object>>() {})
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
