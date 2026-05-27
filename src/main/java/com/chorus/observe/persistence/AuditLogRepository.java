package com.chorus.observe.persistence;

import com.chorus.observe.model.AuditLog;
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

public class AuditLogRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<AuditLog> rowMapper;

    public AuditLogRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new AuditLogRowMapper(mapper);
    }

    public void save(@NonNull AuditLog log) {
        String sql = """
            INSERT INTO audit_logs (log_id, tenant_id, user_id, action, resource_type, resource_id, old_value, new_value, ip_address, user_agent, success, details, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?::jsonb, ?)
            """;
        jdbc.update(sql,
            log.logId(), log.tenantId(), log.userId(), log.action(),
            log.resourceType(), log.resourceId(),
            toJson(log.oldValue()), toJson(log.newValue()),
            log.ipAddress(), log.userAgent(), log.success(),
            toJson(log.details()), Timestamp.from(log.createdAt()));
    }

    public @NonNull Optional<AuditLog> findById(@NonNull String logId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM audit_logs WHERE log_id = ?", rowMapper, logId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<AuditLog> findByTenant(@NonNull String tenantId, int limit, int offset) {
        return jdbc.query("SELECT * FROM audit_logs WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
    }

    /** Same as {@link #findByTenant} but LEFT JOINs users to include email as display name. */
    public @NonNull List<AuditLogEntry> findByTenantWithUsername(@NonNull String tenantId, int limit, int offset) {
        return jdbc.query("""
            SELECT a.*, u.email AS user_email, u.display_name AS user_display_name
            FROM audit_logs a
            LEFT JOIN users u ON u.user_id = a.user_id AND u.tenant_id = a.tenant_id
            WHERE a.tenant_id = ?
            ORDER BY a.created_at DESC
            LIMIT ? OFFSET ?
            """,
            (rs, i) -> {
                AuditLog log = rowMapper.mapRow(rs, i);
                String email = rs.getString("user_email");
                String displayName = rs.getString("user_display_name");
                String username = displayName != null && !displayName.isBlank() ? displayName : email;
                return new AuditLogEntry(log, username);
            },
            tenantId, limit, offset);
    }

    public record AuditLogEntry(@NonNull AuditLog log, String username) {}

    public @NonNull List<AuditLog> findByTenantAndAction(@NonNull String tenantId, @NonNull String action, int limit, int offset) {
        return jdbc.query("SELECT * FROM audit_logs WHERE tenant_id = ? AND action = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, action, limit, offset);
    }

    public @NonNull List<AuditLog> findByResource(@NonNull String tenantId, @NonNull String resourceType, @NonNull String resourceId, int limit) {
        return jdbc.query("SELECT * FROM audit_logs WHERE tenant_id = ? AND resource_type = ? AND resource_id = ? ORDER BY created_at DESC LIMIT ?", rowMapper, tenantId, resourceType, resourceId, limit);
    }

    public long countByTenant(@NonNull String tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ?", Long.class, tenantId);
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(Object value) {
        if (value == null) return "null";
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class AuditLogRowMapper implements RowMapper<AuditLog> {
        private final ObjectMapper mapper;

        AuditLogRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public AuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new AuditLog(
                    rs.getString("log_id"),
                    rs.getString("tenant_id"),
                    rs.getString("user_id"),
                    rs.getString("action"),
                    rs.getString("resource_type"),
                    rs.getString("resource_id"),
                    parseJson(rs.getString("old_value")),
                    parseJson(rs.getString("new_value")),
                    rs.getString("ip_address"),
                    rs.getString("user_agent"),
                    rs.getBoolean("success"),
                    mapper.readValue(rs.getString("details"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }

        private Map<String, Object> parseJson(String json) throws JsonProcessingException {
            if (json == null || json.equals("null")) return null;
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        }
    }
}
