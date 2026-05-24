package com.chorus.observe.persistence;

import com.chorus.observe.model.MultiTurnTurn;
import com.chorus.observe.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
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

public class MultiTurnTurnRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<MultiTurnTurn> rowMapper;

    public MultiTurnTurnRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new TurnRowMapper(mapper);
    }

    public void save(@NonNull MultiTurnTurn turn) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO multi_turn_turns (turn_id, tenant_id, run_id, turn_index, role, input_message, agent_output, expected_keywords, matched_keywords, score, passed, latency_ms, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (turn_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                agent_output = EXCLUDED.agent_output,
                matched_keywords = EXCLUDED.matched_keywords,
                score = EXCLUDED.score,
                passed = EXCLUDED.passed,
                latency_ms = EXCLUDED.latency_ms,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            turn.turnId(), tenantId != null ? tenantId : "default", turn.runId(), turn.turnIndex(), turn.role(),
            turn.inputMessage(), turn.agentOutput(),
            toJson(turn.expectedKeywords()), toJson(turn.matchedKeywords()),
            turn.score(), turn.passed(), turn.latencyMs(),
            toJson(turn.metadata()), Timestamp.from(turn.createdAt()));
    }

    public @NonNull List<MultiTurnTurn> findByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM multi_turn_turns WHERE run_id = ? AND tenant_id = ? ORDER BY turn_index ASC", rowMapper, runId, tenantId);
        }
        return jdbc.query("SELECT * FROM multi_turn_turns WHERE run_id = ? ORDER BY turn_index ASC", rowMapper, runId);
    }

    public void deleteByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM multi_turn_turns WHERE run_id = ? AND tenant_id = ?", runId, tenantId);
        } else {
            jdbc.update("DELETE FROM multi_turn_turns WHERE run_id = ?", runId);
        }
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class TurnRowMapper implements RowMapper<MultiTurnTurn> {
        private final ObjectMapper mapper;

        TurnRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public MultiTurnTurn mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new MultiTurnTurn(
                    rs.getString("turn_id"),
                    rs.getString("run_id"),
                    rs.getInt("turn_index"),
                    rs.getString("role"),
                    rs.getString("input_message"),
                    rs.getString("agent_output"),
                    mapper.readValue(rs.getString("expected_keywords"), new TypeReference<List<String>>() {}),
                    mapper.readValue(rs.getString("matched_keywords"), new TypeReference<List<String>>() {}),
                    rs.getDouble("score"),
                    rs.getBoolean("passed"),
                    rs.getLong("latency_ms"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
