package com.chorus.observe.persistence;

import com.chorus.observe.model.EvalResultRecord;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EvalResultRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<EvalResultRecord> rowMapper = new EvalResultRowMapper();

    public EvalResultRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull EvalResultRecord result) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO eval_results (result_id, eval_run_id, item_id, run_id, span_id, actual_output, score, passed, latency_ms, reasoning, created_at, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (result_id) DO UPDATE SET
                eval_run_id = EXCLUDED.eval_run_id,
                item_id = EXCLUDED.item_id,
                run_id = EXCLUDED.run_id,
                span_id = EXCLUDED.span_id,
                actual_output = EXCLUDED.actual_output,
                score = EXCLUDED.score,
                passed = EXCLUDED.passed,
                latency_ms = EXCLUDED.latency_ms,
                reasoning = EXCLUDED.reasoning
            """;
        jdbc.update(sql,
            result.resultId(), result.evalRunId(), result.itemId(),
            result.runId(), result.spanId(), result.actualOutput(),
            result.score(), result.passed(), result.latencyMs(),
            result.reasoning(), Timestamp.from(result.createdAt()),
            tenantId != null ? tenantId : "default"
        );
    }

    public @NonNull Optional<EvalResultRecord> findById(@NonNull String resultId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM eval_results WHERE result_id = ? AND tenant_id = ?", rowMapper, resultId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM eval_results WHERE result_id = ?", rowMapper, resultId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<EvalResultRecord> findByEvalRunId(@NonNull String evalRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM eval_results WHERE eval_run_id = ? AND tenant_id = ? ORDER BY created_at", rowMapper, evalRunId, tenantId);
        }
        return jdbc.query("SELECT * FROM eval_results WHERE eval_run_id = ? ORDER BY created_at", rowMapper, evalRunId);
    }

    public @NonNull List<EvalResultRecord> findByEvalRunId(@NonNull String evalRunId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM eval_results WHERE eval_run_id = ? AND tenant_id = ? ORDER BY created_at LIMIT ? OFFSET ?", rowMapper, evalRunId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM eval_results WHERE eval_run_id = ? ORDER BY created_at LIMIT ? OFFSET ?", rowMapper, evalRunId, limit, offset);
    }

    public long countByEvalRunId(@NonNull String evalRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_results WHERE eval_run_id = ? AND tenant_id = ?", Long.class, evalRunId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_results WHERE eval_run_id = ?", Long.class, evalRunId);
        return count != null ? count : 0L;
    }

    public @NonNull List<EvalResultRecord> findByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM eval_results WHERE run_id = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, runId, tenantId);
        }
        return jdbc.query("SELECT * FROM eval_results WHERE run_id = ? ORDER BY created_at DESC", rowMapper, runId);
    }

    public @NonNull List<EvalResultRecord> findByRunId(@NonNull String runId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM eval_results WHERE run_id = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, runId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM eval_results WHERE run_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, runId, limit, offset);
    }

    public long countByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_results WHERE run_id = ? AND tenant_id = ?", Long.class, runId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_results WHERE run_id = ?", Long.class, runId);
        return count != null ? count : 0L;
    }

    public @NonNull List<EvalResultRecord> findByRunIds(@NonNull List<String> runIds) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(runIds.size(), "?"));
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            String sql = "SELECT * FROM eval_results WHERE run_id IN (" + placeholders + ") AND tenant_id = ? ORDER BY created_at DESC";
            List<Object> params = new ArrayList<>(runIds);
            params.add(tenantId);
            return jdbc.query(sql, rowMapper, params.toArray());
        }
        String sql = "SELECT * FROM eval_results WHERE run_id IN (" + placeholders + ") ORDER BY created_at DESC";
        return jdbc.query(sql, rowMapper, runIds.toArray());
    }

    public long countPassedByEvalRunId(@NonNull String evalRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_results WHERE eval_run_id = ? AND passed = TRUE AND tenant_id = ?", Long.class, evalRunId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_results WHERE eval_run_id = ? AND passed = TRUE", Long.class, evalRunId);
        return count != null ? count : 0L;
    }

    public double avgScoreByEvalRunId(@NonNull String evalRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Double avg = jdbc.queryForObject("SELECT AVG(score) FROM eval_results WHERE eval_run_id = ? AND tenant_id = ?", Double.class, evalRunId, tenantId);
            return avg != null ? avg : 0.0;
        }
        Double avg = jdbc.queryForObject("SELECT AVG(score) FROM eval_results WHERE eval_run_id = ?", Double.class, evalRunId);
        return avg != null ? avg : 0.0;
    }

    public void deleteByEvalRunId(@NonNull String evalRunId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM eval_results WHERE eval_run_id = ? AND tenant_id = ?", evalRunId, tenantId);
        } else {
            jdbc.update("DELETE FROM eval_results WHERE eval_run_id = ?", evalRunId);
        }
    }

    private static final class EvalResultRowMapper implements RowMapper<EvalResultRecord> {
        @Override
        public EvalResultRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EvalResultRecord(
                rs.getString("result_id"),
                rs.getString("eval_run_id"),
                rs.getString("item_id"),
                rs.getString("run_id"),
                rs.getString("span_id"),
                rs.getString("actual_output"),
                rs.getDouble("score"),
                rs.getBoolean("passed"),
                rs.getLong("latency_ms"),
                rs.getString("reasoning"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
