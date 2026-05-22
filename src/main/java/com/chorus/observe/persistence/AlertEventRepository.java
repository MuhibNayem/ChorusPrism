package com.chorus.observe.persistence;

import com.chorus.observe.model.AlertEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
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
import java.util.Optional;

/**
 * JDBC repository for alert events.
 */
public class AlertEventRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<AlertEvent> rowMapper;

    public AlertEventRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new AlertEventRowMapper(mapper);
    }

    public void save(@NonNull AlertEvent event) {
        String sql = """
            INSERT INTO alert_events (event_id, rule_id, triggered_at, value, resolved_at, notification_sent, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (event_id) DO UPDATE SET
                rule_id = EXCLUDED.rule_id,
                triggered_at = EXCLUDED.triggered_at,
                value = EXCLUDED.value,
                resolved_at = EXCLUDED.resolved_at,
                notification_sent = EXCLUDED.notification_sent,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            event.eventId(), event.ruleId(), Timestamp.from(event.triggeredAt()),
            event.value(), event.resolvedAt() != null ? Timestamp.from(event.resolvedAt()) : null,
            event.notificationSent(), toJson(event.metadata()),
            Timestamp.from(event.createdAt())
        );
    }

    public @NonNull Optional<AlertEvent> findById(@NonNull String eventId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM alert_events WHERE event_id = ?", rowMapper, eventId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<AlertEvent> findByRuleId(@NonNull String ruleId) {
        return jdbc.query("SELECT * FROM alert_events WHERE rule_id = ? ORDER BY triggered_at DESC", rowMapper, ruleId);
    }

    public @NonNull List<AlertEvent> findByRuleId(@NonNull String ruleId, int limit, int offset) {
        return jdbc.query("SELECT * FROM alert_events WHERE rule_id = ? ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, ruleId, limit, offset);
    }

    public long countByRuleId(@NonNull String ruleId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events WHERE rule_id = ?", Long.class, ruleId);
        return count != null ? count : 0L;
    }

    public @NonNull List<AlertEvent> findUnresolved() {
        return jdbc.query("SELECT * FROM alert_events WHERE resolved_at IS NULL ORDER BY triggered_at DESC", rowMapper);
    }

    public @NonNull List<AlertEvent> findUnresolved(int limit, int offset) {
        return jdbc.query("SELECT * FROM alert_events WHERE resolved_at IS NULL ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long countUnresolved() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events WHERE resolved_at IS NULL", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<AlertEvent> findRecent(int limit) {
        return jdbc.query("SELECT * FROM alert_events ORDER BY triggered_at DESC LIMIT ?", rowMapper, limit);
    }

    public @NonNull List<AlertEvent> findRecent(int limit, int offset) {
        return jdbc.query("SELECT * FROM alert_events ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull Optional<AlertEvent> findMostRecentByRuleId(@NonNull String ruleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM alert_events WHERE rule_id = ? ORDER BY triggered_at DESC LIMIT 1", rowMapper, ruleId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class AlertEventRowMapper implements RowMapper<AlertEvent> {
        private final ObjectMapper mapper;

        AlertEventRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public AlertEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new AlertEvent(
                    rs.getString("event_id"),
                    rs.getString("rule_id"),
                    rs.getTimestamp("triggered_at").toInstant(),
                    rs.getDouble("value"),
                    rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
                    rs.getBoolean("notification_sent"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
