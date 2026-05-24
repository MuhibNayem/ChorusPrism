package com.chorus.observe.persistence;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.security.TenantContext;
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
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO alert_events (event_id, tenant_id, rule_id, triggered_at, value, resolved_at, notification_sent, metadata, created_at, retry_count, next_retry_at, last_error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                rule_id = EXCLUDED.rule_id,
                triggered_at = EXCLUDED.triggered_at,
                value = EXCLUDED.value,
                resolved_at = EXCLUDED.resolved_at,
                notification_sent = EXCLUDED.notification_sent,
                metadata = EXCLUDED.metadata,
                retry_count = EXCLUDED.retry_count,
                next_retry_at = EXCLUDED.next_retry_at,
                last_error = EXCLUDED.last_error
            """;
        jdbc.update(sql,
            event.eventId(), tenantId != null ? tenantId : "default", event.ruleId(), Timestamp.from(event.triggeredAt()),
            event.value(), event.resolvedAt() != null ? Timestamp.from(event.resolvedAt()) : null,
            event.notificationSent(), toJson(event.metadata()),
            Timestamp.from(event.createdAt()), event.retryCount(),
            event.nextRetryAt() != null ? Timestamp.from(event.nextRetryAt()) : null,
            event.lastError()
        );
    }

    public @NonNull Optional<AlertEvent> findById(@NonNull String eventId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM alert_events WHERE event_id = ? AND tenant_id = ?", rowMapper, eventId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM alert_events WHERE event_id = ?", rowMapper, eventId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<AlertEvent> findByRuleId(@NonNull String ruleId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM alert_events WHERE rule_id = ? AND tenant_id = ? ORDER BY triggered_at DESC", rowMapper, ruleId, tenantId);
        }
        return jdbc.query("SELECT * FROM alert_events WHERE rule_id = ? ORDER BY triggered_at DESC", rowMapper, ruleId);
    }

    public @NonNull List<AlertEvent> findByRuleId(@NonNull String ruleId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM alert_events WHERE rule_id = ? AND tenant_id = ? ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, ruleId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM alert_events WHERE rule_id = ? ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, ruleId, limit, offset);
    }

    public long countByRuleId(@NonNull String ruleId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events WHERE rule_id = ? AND tenant_id = ?", Long.class, ruleId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events WHERE rule_id = ?", Long.class, ruleId);
        return count != null ? count : 0L;
    }

    public @NonNull List<AlertEvent> findUnresolved() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM alert_events WHERE resolved_at IS NULL AND tenant_id = ? ORDER BY triggered_at DESC", rowMapper, tenantId);
        }
        return jdbc.query("SELECT * FROM alert_events WHERE resolved_at IS NULL ORDER BY triggered_at DESC", rowMapper);
    }

    public @NonNull List<AlertEvent> findUnresolved(int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM alert_events WHERE resolved_at IS NULL AND tenant_id = ? ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM alert_events WHERE resolved_at IS NULL ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long countUnresolved() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events WHERE resolved_at IS NULL AND tenant_id = ?", Long.class, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events WHERE resolved_at IS NULL", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<AlertEvent> findRecent(int limit) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM alert_events WHERE tenant_id = ? ORDER BY triggered_at DESC LIMIT ?", rowMapper, tenantId, limit);
        }
        return jdbc.query("SELECT * FROM alert_events ORDER BY triggered_at DESC LIMIT ?", rowMapper, limit);
    }

    public @NonNull List<AlertEvent> findRecent(int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM alert_events WHERE tenant_id = ? ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM alert_events ORDER BY triggered_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events WHERE tenant_id = ?", Long.class, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_events", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull Optional<AlertEvent> findMostRecentByRuleId(@NonNull String ruleId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM alert_events WHERE rule_id = ? AND tenant_id = ? ORDER BY triggered_at DESC LIMIT 1", rowMapper, ruleId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM alert_events WHERE rule_id = ? ORDER BY triggered_at DESC LIMIT 1", rowMapper, ruleId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<AlertEvent> findRetryable() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query(
                "SELECT * FROM alert_events WHERE next_retry_at <= ? AND retry_count < 3 AND tenant_id = ? ORDER BY next_retry_at",
                rowMapper, Timestamp.from(Instant.now()), tenantId);
        }
        return jdbc.query(
            "SELECT * FROM alert_events WHERE next_retry_at <= ? AND retry_count < 3 ORDER BY next_retry_at",
            rowMapper, Timestamp.from(Instant.now()));
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
                Timestamp nextRetryTs = rs.getTimestamp("next_retry_at");
                return new AlertEvent(
                    rs.getString("event_id"),
                    rs.getString("rule_id"),
                    rs.getTimestamp("triggered_at").toInstant(),
                    rs.getDouble("value"),
                    rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
                    rs.getBoolean("notification_sent"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getInt("retry_count"),
                    nextRetryTs != null ? nextRetryTs.toInstant() : null,
                    rs.getString("last_error")
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
