package com.chorus.observe.persistence;

import com.chorus.observe.model.Span;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC repository for spans.
 */
public class SpanRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Span> rowMapper;

    public SpanRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new SpanRowMapper(mapper);
    }

    public void save(@NonNull Span span) {
        String sql = """
            INSERT INTO spans (span_id, run_id, parent_span_id, span_name, kind, start_time, end_time, attributes, events, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (span_id) DO UPDATE SET
                run_id = EXCLUDED.run_id,
                parent_span_id = EXCLUDED.parent_span_id,
                span_name = EXCLUDED.span_name,
                kind = EXCLUDED.kind,
                start_time = EXCLUDED.start_time,
                end_time = EXCLUDED.end_time,
                attributes = EXCLUDED.attributes,
                events = EXCLUDED.events,
                status = EXCLUDED.status
            """;
        jdbc.update(sql,
            span.spanId(), span.runId(), span.parentSpanId(), span.spanName(),
            span.kind().name(),
            Timestamp.from(span.startTime()),
            span.endTime() != null ? Timestamp.from(span.endTime()) : null,
            toJson(span.attributes()),
            toJson(span.events()),
            span.status().name()
        );
    }

    public @NonNull List<Span> findByRunId(@NonNull String runId) {
        return jdbc.query(
            "SELECT * FROM spans WHERE run_id = ? ORDER BY start_time ASC",
            rowMapper, runId);
    }

    public @NonNull List<Span> findByRunId(@NonNull String runId, int limit, int offset) {
        return jdbc.query(
            "SELECT * FROM spans WHERE run_id = ? ORDER BY start_time ASC LIMIT ? OFFSET ?",
            rowMapper, runId, limit, offset);
    }

    public long countByRunId(@NonNull String runId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM spans WHERE run_id = ?", Long.class, runId);
        return count != null ? count : 0L;
    }

    public @NonNull List<Span> findByRunIdAndKind(@NonNull String runId, Span.@NonNull Kind kind) {
        return jdbc.query(
            "SELECT * FROM spans WHERE run_id = ? AND kind = ? ORDER BY start_time ASC",
            rowMapper, runId, kind.name());
    }

    public void deleteByRunId(@NonNull String runId) {
        jdbc.update("DELETE FROM spans WHERE run_id = ?", runId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class SpanRowMapper implements RowMapper<Span> {
        private final ObjectMapper mapper;

        SpanRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Span mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Span(
                    rs.getString("span_id"),
                    rs.getString("run_id"),
                    rs.getString("parent_span_id"),
                    rs.getString("span_name"),
                    Span.Kind.valueOf(rs.getString("kind")),
                    rs.getTimestamp("start_time").toInstant(),
                    rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant() : null,
                    mapper.readValue(rs.getString("attributes"), new TypeReference<Map<String, Object>>() {}),
                    mapper.readValue(rs.getString("events"), new TypeReference<List<Span.SpanEvent>>() {}),
                    Span.Status.valueOf(rs.getString("status"))
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
