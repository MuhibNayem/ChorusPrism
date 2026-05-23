package com.chorus.observe.store;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.Span;
import com.chorus.observe.model.ToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ClickHouse-backed {@link SpanStore} optimized for high-throughput append-only ingestion.
 * <p>
 * ClickHouse dialect differences handled:
 * <ul>
 *   <li>No {@code ON CONFLICT} — pure INSERT</li>
 *   <li>{@code DateTime} instead of {@code TIMESTAMPTZ}</li>
 *   <li>{@code String} instead of {@code JSONB}</li>
 *   <li>{@code Array(String)} for finish_reasons</li>
 *   <li>Batch inserts for throughput</li>
 * </ul>
 */
public class ClickHouseSpanStore implements SpanStore {

    private static final Logger LOG = LoggerFactory.getLogger(ClickHouseSpanStore.class);

    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public ClickHouseSpanStore(@NonNull DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public ClickHouseSpanStore(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void saveSpans(@NonNull List<Span> spans) {
        if (spans.isEmpty()) return;
        String sql = """
            INSERT INTO ch_spans (span_id, run_id, parent_span_id, span_name, kind, start_time, end_time, attributes, events, status, span_type, first_token_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Span span : spans) {
                ps.setString(1, span.spanId());
                ps.setString(2, span.runId());
                ps.setString(3, span.parentSpanId());
                ps.setString(4, span.spanName());
                ps.setString(5, span.kind().name());
                ps.setTimestamp(6, Timestamp.from(span.startTime()));
                ps.setTimestamp(7, span.endTime() != null ? Timestamp.from(span.endTime()) : null);
                ps.setString(8, toJson(span.attributes()));
                ps.setString(9, toJson(span.events()));
                ps.setString(10, span.status().name());
                ps.setString(11, span.spanType());
                ps.setTimestamp(12, span.firstTokenAt() != null ? Timestamp.from(span.firstTokenAt()) : null);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            LOG.error("Failed to batch insert {} spans", spans.size(), e);
        }
    }

    @Override
    public void saveLlmCalls(@NonNull List<LlmCall> calls) {
        if (calls.isEmpty()) return;
        String sql = """
            INSERT INTO ch_llm_calls (call_id, span_id, run_id, provider, model, input_tokens, output_tokens, cost_usd, latency_ms, prompt, completion, finish_reasons, messages)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (LlmCall call : calls) {
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
                ps.setString(12, String.join(",", call.finishReasons()));
                ps.setString(13, call.messages() != null ? toJson(call.messages()) : null);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            LOG.error("Failed to batch insert {} LLM calls", calls.size(), e);
        }
    }

    @Override
    public void saveToolCalls(@NonNull List<ToolCall> calls) {
        if (calls.isEmpty()) return;
        String sql = """
            INSERT INTO ch_tool_calls (call_id, span_id, run_id, tool_name, args, result, latency_ms, error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ToolCall call : calls) {
                ps.setString(1, call.callId());
                ps.setString(2, call.spanId());
                ps.setString(3, call.runId());
                ps.setString(4, call.toolName());
                ps.setString(5, call.args());
                ps.setString(6, call.result());
                ps.setLong(7, call.latencyMs());
                ps.setString(8, call.error());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            LOG.error("Failed to batch insert {} tool calls", calls.size(), e);
        }
    }

    @Override
    public @NonNull List<Span> findSpansByRunId(@NonNull String runId) {
        String sql = "SELECT * FROM ch_spans WHERE run_id = ? ORDER BY start_time ASC";
        List<Span> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSpan(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to query spans for run {}", runId, e);
        }
        return result;
    }

    @Override
    public @NonNull List<LlmCall> findLlmCallsByRunId(@NonNull String runId) {
        String sql = "SELECT * FROM ch_llm_calls WHERE run_id = ? ORDER BY created_at ASC";
        List<LlmCall> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapLlmCall(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to query LLM calls for run {}", runId, e);
        }
        return result;
    }

    @Override
    public @NonNull List<ToolCall> findToolCallsByRunId(@NonNull String runId) {
        String sql = "SELECT * FROM ch_tool_calls WHERE run_id = ? ORDER BY created_at ASC";
        List<ToolCall> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapToolCall(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to query tool calls for run {}", runId, e);
        }
        return result;
    }

    @Override
    public boolean isHealthy() {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private Span mapSpan(ResultSet rs) throws SQLException {
        Map<String, Object> attrs = parseJson(rs.getString("attributes"), new TypeReference<>() {});
        List<Span.SpanEvent> events = parseJson(rs.getString("events"), new TypeReference<>() {});
        return new Span(
            rs.getString("span_id"),
            rs.getString("run_id"),
            rs.getString("parent_span_id"),
            rs.getString("span_name"),
            Span.Kind.valueOf(rs.getString("kind")),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant() : null,
            attrs != null ? attrs : Map.of(),
            events != null ? events : List.of(),
            Span.Status.valueOf(rs.getString("status")),
            rs.getString("span_type"),
            rs.getTimestamp("first_token_at") != null ? rs.getTimestamp("first_token_at").toInstant() : null
        );
    }

    private LlmCall mapLlmCall(ResultSet rs) throws SQLException {
        String fr = rs.getString("finish_reasons");
        List<String> finishReasons = fr != null && !fr.isEmpty() ? java.util.List.of(fr.split(",")) : java.util.List.of();
        String messagesJson = rs.getString("messages");
        List<LlmCall.LlmMessage> messages = messagesJson != null && !messagesJson.isEmpty() && !messagesJson.equals("{}")
            ? parseJson(messagesJson, new TypeReference<>() {})
            : null;
        return new LlmCall(
            rs.getString("call_id"),
            rs.getString("span_id"),
            rs.getString("run_id"),
            rs.getString("provider"),
            rs.getString("model"),
            rs.getInt("input_tokens"),
            rs.getInt("output_tokens"),
            rs.getBigDecimal("cost_usd") != null ? rs.getBigDecimal("cost_usd") : BigDecimal.ZERO,
            rs.getLong("latency_ms"),
            rs.getString("prompt"),
            rs.getString("completion"),
            finishReasons,
            messages
        );
    }

    private ToolCall mapToolCall(ResultSet rs) throws SQLException {
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

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private <T> T parseJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return null;
        }
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON: {}", json, e);
            return null;
        }
    }
}
