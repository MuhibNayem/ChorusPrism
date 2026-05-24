package com.chorus.observe.persistence;

import com.chorus.observe.model.EvalResultRun;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * JDBC repository for individual N-run evaluation results.
 */
public class EvalResultRunRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<EvalResultRun> rowMapper = new EvalResultRunRowMapper();

    public EvalResultRunRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull EvalResultRun resultRun) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO eval_result_runs (result_run_id, tenant_id, result_id, run_number, score, passed, actual_output, reasoning, latency_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (result_id, run_number) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                score = EXCLUDED.score,
                passed = EXCLUDED.passed,
                actual_output = EXCLUDED.actual_output,
                reasoning = EXCLUDED.reasoning,
                latency_ms = EXCLUDED.latency_ms
            """;
        jdbc.update(sql,
            resultRun.resultRunId(), tenantId != null ? tenantId : "default", resultRun.resultId(), resultRun.runNumber(),
            resultRun.score(), resultRun.passed(), resultRun.actualOutput(),
            resultRun.reasoning(), resultRun.latencyMs(),
            Timestamp.from(resultRun.createdAt())
        );
    }

    public @NonNull List<EvalResultRun> findByResultId(@NonNull String resultId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query(
                "SELECT * FROM eval_result_runs WHERE result_id = ? AND tenant_id = ? ORDER BY run_number",
                rowMapper, resultId, tenantId);
        }
        return jdbc.query(
            "SELECT * FROM eval_result_runs WHERE result_id = ? ORDER BY run_number",
            rowMapper, resultId);
    }

    public void deleteByResultId(@NonNull String resultId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM eval_result_runs WHERE result_id = ? AND tenant_id = ?", resultId, tenantId);
        } else {
            jdbc.update("DELETE FROM eval_result_runs WHERE result_id = ?", resultId);
        }
    }

    private static final class EvalResultRunRowMapper implements RowMapper<EvalResultRun> {
        @Override
        public EvalResultRun mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EvalResultRun(
                rs.getString("result_run_id"),
                rs.getString("result_id"),
                rs.getInt("run_number"),
                rs.getDouble("score"),
                rs.getBoolean("passed"),
                rs.getString("actual_output"),
                rs.getString("reasoning"),
                rs.getLong("latency_ms"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
