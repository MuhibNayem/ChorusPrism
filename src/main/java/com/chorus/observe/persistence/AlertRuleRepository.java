package com.chorus.observe.persistence;

import com.chorus.observe.model.AlertRule;
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
 * JDBC repository for alert rules.
 */
public class AlertRuleRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<AlertRule> rowMapper;

    public AlertRuleRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new AlertRuleRowMapper(mapper);
    }

    public void save(@NonNull AlertRule rule) {
        String sql = """
            INSERT INTO alert_rules (rule_id, name, condition_expr, threshold, severity, webhook_url, email, enabled, cooldown_seconds, metadata, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (rule_id) DO UPDATE SET
                name = EXCLUDED.name,
                condition_expr = EXCLUDED.condition_expr,
                threshold = EXCLUDED.threshold,
                severity = EXCLUDED.severity,
                webhook_url = EXCLUDED.webhook_url,
                email = EXCLUDED.email,
                enabled = EXCLUDED.enabled,
                cooldown_seconds = EXCLUDED.cooldown_seconds,
                metadata = EXCLUDED.metadata,
                updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            rule.ruleId(), rule.name(), rule.conditionExpr(), rule.threshold(),
            rule.severity().name(), rule.webhookUrl(), rule.email(),
            rule.enabled(), rule.cooldownSeconds(), toJson(rule.metadata()),
            Timestamp.from(rule.createdAt()), Timestamp.from(rule.updatedAt())
        );
    }

    public @NonNull Optional<AlertRule> findById(@NonNull String ruleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM alert_rules WHERE rule_id = ?", rowMapper, ruleId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<AlertRule> findAll() {
        return jdbc.query("SELECT * FROM alert_rules ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<AlertRule> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM alert_rules ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_rules", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<AlertRule> findEnabled() {
        return jdbc.query("SELECT * FROM alert_rules WHERE enabled = TRUE ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<AlertRule> findEnabled(int limit, int offset) {
        return jdbc.query("SELECT * FROM alert_rules WHERE enabled = TRUE ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long countEnabled() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM alert_rules WHERE enabled = TRUE", Long.class);
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String ruleId) {
        jdbc.update("DELETE FROM alert_rules WHERE rule_id = ?", ruleId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class AlertRuleRowMapper implements RowMapper<AlertRule> {
        private final ObjectMapper mapper;

        AlertRuleRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public AlertRule mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new AlertRule(
                    rs.getString("rule_id"),
                    rs.getString("name"),
                    rs.getString("condition_expr"),
                    rs.getDouble("threshold"),
                    AlertRule.Severity.valueOf(rs.getString("severity")),
                    rs.getString("webhook_url"),
                    rs.getString("email"),
                    rs.getBoolean("enabled"),
                    rs.getInt("cooldown_seconds"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
