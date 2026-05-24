package com.chorus.observe.persistence;

import com.chorus.observe.model.LlmCall;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * JDBC repository for LLM calls.
 */
public class LlmCallRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<LlmCall> rowMapper;

    public LlmCallRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new LlmCallRowMapper(mapper);
    }

    public void save(@NonNull LlmCall call) {
        String sql = """
            INSERT INTO llm_calls (call_id, span_id, run_id, provider, model, input_tokens, output_tokens, cost_usd, latency_ms, prompt, completion, finish_reasons, messages)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
            ON CONFLICT (call_id) DO UPDATE SET
                span_id = EXCLUDED.span_id,
                run_id = EXCLUDED.run_id,
                provider = EXCLUDED.provider,
                model = EXCLUDED.model,
                input_tokens = EXCLUDED.input_tokens,
                output_tokens = EXCLUDED.output_tokens,
                cost_usd = EXCLUDED.cost_usd,
                latency_ms = EXCLUDED.latency_ms,
                prompt = EXCLUDED.prompt,
                completion = EXCLUDED.completion,
                finish_reasons = EXCLUDED.finish_reasons,
                messages = EXCLUDED.messages
            """;
        jdbc.update(sql,
            call.callId(), call.spanId(), call.runId(), call.provider(), call.model(),
            call.inputTokens(), call.outputTokens(), call.costUsd(), call.latencyMs(),
            call.prompt(), call.completion(), toJson(call.finishReasons()),
            call.messages() != null ? toJson(call.messages()) : null
        );
    }

    public void saveAll(@NonNull List<LlmCall> calls) {
        if (calls.isEmpty()) return;
        String sql = """
            INSERT INTO llm_calls (call_id, span_id, run_id, provider, model, input_tokens, output_tokens, cost_usd, latency_ms, prompt, completion, finish_reasons, messages)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
            ON CONFLICT (call_id) DO UPDATE SET
                span_id = EXCLUDED.span_id,
                run_id = EXCLUDED.run_id,
                provider = EXCLUDED.provider,
                model = EXCLUDED.model,
                input_tokens = EXCLUDED.input_tokens,
                output_tokens = EXCLUDED.output_tokens,
                cost_usd = EXCLUDED.cost_usd,
                latency_ms = EXCLUDED.latency_ms,
                prompt = EXCLUDED.prompt,
                completion = EXCLUDED.completion,
                finish_reasons = EXCLUDED.finish_reasons,
                messages = EXCLUDED.messages
            """;
        jdbc.batchUpdate(sql, calls, calls.size(), (ps, call) -> {
            ps.setString(1, call.callId());
            ps.setString(2, call.spanId());
            ps.setString(3, call.runId());
            ps.setString(4, call.provider());
            ps.setString(5, call.model());
            ps.setInt(6, call.inputTokens());
            ps.setInt(7, call.outputTokens());
            ps.setBigDecimal(8, call.costUsd());
            ps.setLong(9, call.latencyMs());
            ps.setString(10, call.prompt());
            ps.setString(11, call.completion());
            ps.setString(12, toJson(call.finishReasons()));
            ps.setString(13, call.messages() != null ? toJson(call.messages()) : null);
        });
    }

    public @NonNull List<LlmCall> findByRunId(@NonNull String runId) {
        return jdbc.query(
            "SELECT * FROM llm_calls WHERE run_id = ? ORDER BY created_at ASC",
            rowMapper, runId);
    }

    public @NonNull List<LlmCall> findByRunId(@NonNull String runId, int limit, int offset) {
        return jdbc.query(
            "SELECT * FROM llm_calls WHERE run_id = ? ORDER BY created_at ASC LIMIT ? OFFSET ?",
            rowMapper, runId, limit, offset);
    }

    public long countByRunId(@NonNull String runId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM llm_calls WHERE run_id = ?", Long.class, runId);
        return count != null ? count : 0L;
    }

    public @NonNull List<LlmCall> findBySpanId(@NonNull String spanId) {
        return jdbc.query(
            "SELECT * FROM llm_calls WHERE span_id = ? ORDER BY created_at ASC",
            rowMapper, spanId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class LlmCallRowMapper implements RowMapper<LlmCall> {
        private final ObjectMapper mapper;

        LlmCallRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public LlmCall mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new LlmCall(
                    rs.getString("call_id"),
                    rs.getString("span_id"),
                    rs.getString("run_id"),
                    rs.getString("provider"),
                    rs.getString("model"),
                    rs.getInt("input_tokens"),
                    rs.getInt("output_tokens"),
                    rs.getBigDecimal("cost_usd"),
                    rs.getLong("latency_ms"),
                    rs.getString("prompt"),
                    rs.getString("completion"),
                    mapper.readValue(rs.getString("finish_reasons"), new TypeReference<List<String>>() {}),
                    rs.getString("messages") != null
                        ? mapper.readValue(rs.getString("messages"), new TypeReference<List<LlmCall.LlmMessage>>() {})
                        : null
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
