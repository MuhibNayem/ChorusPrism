package com.chorus.observe.persistence;

import com.chorus.observe.model.EvalLoop;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * JDBC repository for continuous evaluation loops.
 */
public class EvalLoopRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<EvalLoop> rowMapper = new EvalLoopRowMapper();

    public EvalLoopRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull EvalLoop loop) {
        String sql = """
            INSERT INTO eval_loops (loop_id, agent_id, evaluator_id, sampling_rate, alert_threshold, status, created_at, last_run_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (loop_id) DO UPDATE SET
                agent_id = EXCLUDED.agent_id,
                evaluator_id = EXCLUDED.evaluator_id,
                sampling_rate = EXCLUDED.sampling_rate,
                alert_threshold = EXCLUDED.alert_threshold,
                status = EXCLUDED.status,
                last_run_at = EXCLUDED.last_run_at
            """;
        jdbc.update(sql,
            loop.loopId(), loop.agentId(), loop.evaluatorId(),
            loop.samplingRate(), loop.alertThreshold(), loop.status(),
            Timestamp.from(loop.createdAt()),
            loop.lastRunAt() != null ? Timestamp.from(loop.lastRunAt()) : null
        );
    }

    public @NonNull Optional<EvalLoop> findById(@NonNull String loopId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM eval_loops WHERE loop_id = ?", rowMapper, loopId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<EvalLoop> findAll() {
        return jdbc.query("SELECT * FROM eval_loops ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<EvalLoop> findByAgentId(@NonNull String agentId) {
        return jdbc.query("SELECT * FROM eval_loops WHERE agent_id = ? ORDER BY created_at DESC", rowMapper, agentId);
    }

    public void deleteById(@NonNull String loopId) {
        jdbc.update("DELETE FROM eval_loops WHERE loop_id = ?", loopId);
    }

    private static final class EvalLoopRowMapper implements RowMapper<EvalLoop> {
        @Override
        public EvalLoop mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp lastRun = rs.getTimestamp("last_run_at");
            return new EvalLoop(
                rs.getString("loop_id"),
                rs.getString("agent_id"),
                rs.getString("evaluator_id"),
                rs.getInt("sampling_rate"),
                rs.getDouble("alert_threshold"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                lastRun != null ? lastRun.toInstant() : null
            );
        }
    }
}
