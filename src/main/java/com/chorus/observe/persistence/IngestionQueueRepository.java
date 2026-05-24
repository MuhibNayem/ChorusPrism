package com.chorus.observe.persistence;

import com.chorus.observe.model.Span;
import com.chorus.observe.service.OtlpIngestionService.OtlpSpan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC Repository for the transactional ingestion outbox queue.
 */
public class IngestionQueueRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<QueueRecord> rowMapper;

    public IngestionQueueRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new QueueRecordRowMapper(mapper);
    }

    /**
     * Resiliently enqueue a batch of OTLP spans.
     */
    public void enqueue(@NonNull List<OtlpSpan> spans) {
        if (jdbc == null || spans.isEmpty()) return;

        String sql = """
            INSERT INTO ingestion_queue (queue_id, trace_id, span_id, name, start_time, end_time, kind, status_code, attributes, events, parent_span_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            """;

        jdbc.batchUpdate(sql, spans, 100, (PreparedStatement ps, OtlpSpan span) -> {
            ps.setString(1, "q-" + UUID.randomUUID().toString());
            ps.setString(2, span.traceId());
            ps.setString(3, span.spanId());
            ps.setString(4, span.name());
            ps.setTimestamp(5, Timestamp.from(span.startTime()));
            if (span.endTime() != null) {
                ps.setTimestamp(6, Timestamp.from(span.endTime()));
            } else {
                ps.setNull(6, Types.TIMESTAMP);
            }
            ps.setInt(7, span.kind());
            ps.setInt(8, span.statusCode());
            ps.setString(9, toJson(span.attributes()));
            ps.setString(10, toJson(span.events()));
            ps.setString(11, span.parentSpanId());
        });
    }

    /**
     * Fetch a batch of enqueued queue records ordered by created_at.
     */
    public @NonNull List<QueueRecord> fetchBatch(int limit) {
        if (jdbc == null) return List.of();
        return jdbc.query("SELECT * FROM ingestion_queue ORDER BY created_at ASC LIMIT ?", rowMapper, limit);
    }

    /**
     * Delete processed queue records in bulk.
     */
    public void dequeue(@NonNull List<String> queueIds) {
        if (jdbc == null || queueIds.isEmpty()) return;

        List<Object[]> batchArgs = new ArrayList<>(queueIds.size());
        for (String id : queueIds) {
            batchArgs.add(new Object[]{id});
        }

        jdbc.batchUpdate("DELETE FROM ingestion_queue WHERE queue_id = ?", batchArgs);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    public record QueueRecord(@NonNull String queueId, @NonNull OtlpSpan span) {}

    private static final class QueueRecordRowMapper implements RowMapper<QueueRecord> {
        private final ObjectMapper mapper;

        QueueRecordRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public QueueRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                String attrsJson = rs.getString("attributes");
                Map<String, Object> attributes = mapper.readValue(attrsJson, new TypeReference<Map<String, Object>>() {});

                String eventsJson = rs.getString("events");
                List<Span.SpanEvent> events = mapper.readValue(eventsJson, new TypeReference<List<Span.SpanEvent>>() {});

                Timestamp endTs = rs.getTimestamp("end_time");
                Instant endTime = endTs != null ? endTs.toInstant() : null;

                OtlpSpan span = new OtlpSpan(
                    rs.getString("trace_id"),
                    rs.getString("span_id"),
                    rs.getString("name"),
                    rs.getTimestamp("start_time").toInstant(),
                    endTime,
                    rs.getInt("kind"),
                    rs.getInt("status_code"),
                    attributes,
                    events,
                    rs.getString("parent_span_id")
                );

                return new QueueRecord(rs.getString("queue_id"), span);
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize ingestion queue record JSON", e);
            }
        }
    }
}
