package com.chorus.observe.persistence;

import com.chorus.observe.model.ToolCall;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * JDBC repository for tool calls.
 */
public class ToolCallRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<ToolCall> rowMapper = new ToolCallRowMapper();

    public ToolCallRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull ToolCall call) {
        String sql = """
            INSERT INTO tool_calls (call_id, span_id, run_id, tool_name, args, result, latency_ms, error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (call_id) DO UPDATE SET
                span_id = EXCLUDED.span_id,
                run_id = EXCLUDED.run_id,
                tool_name = EXCLUDED.tool_name,
                args = EXCLUDED.args,
                result = EXCLUDED.result,
                latency_ms = EXCLUDED.latency_ms,
                error = EXCLUDED.error
            """;
        jdbc.update(sql,
            call.callId(), call.spanId(), call.runId(), call.toolName(),
            call.args(), call.result(), call.latencyMs(), call.error()
        );
    }

    public @NonNull List<ToolCall> findByRunId(@NonNull String runId) {
        return jdbc.query(
            "SELECT * FROM tool_calls WHERE run_id = ? ORDER BY created_at ASC",
            rowMapper, runId);
    }

    public @NonNull List<ToolCall> findBySpanId(@NonNull String spanId) {
        return jdbc.query(
            "SELECT * FROM tool_calls WHERE span_id = ? ORDER BY created_at ASC",
            rowMapper, spanId);
    }

    private static final class ToolCallRowMapper implements RowMapper<ToolCall> {
        @Override
        public ToolCall mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ToolCall(
                rs.getString("call_id"),
                rs.getString("span_id"),
                rs.getString("run_id"),
                rs.getString("tool_name"),
                rs.getString("args"),
                rs.getString("result"),
                rs.getLong("latency_ms"),
                rs.getString("error")
            );
        }
    }
}
