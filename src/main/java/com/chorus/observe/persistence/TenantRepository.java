package com.chorus.observe.persistence;

import com.chorus.observe.model.Tenant;
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
import java.util.Objects;
import java.util.Optional;

public class TenantRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Tenant> rowMapper = new TenantRowMapper();

    public TenantRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull Tenant tenant) {
        String sql = """
            INSERT INTO tenants (tenant_id, name, config, status, created_at, updated_at)
            VALUES (?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (tenant_id) DO UPDATE SET
                name = EXCLUDED.name,
                config = EXCLUDED.config,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            tenant.tenantId(), tenant.name(), toJson(tenant.config()),
            tenant.status().name(),
            Timestamp.from(tenant.createdAt()), Timestamp.from(tenant.updatedAt()));
    }

    public @NonNull Optional<Tenant> findById(@NonNull String tenantId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM tenants WHERE tenant_id = ?", rowMapper, tenantId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Tenant> findAll() {
        return jdbc.query("SELECT * FROM tenants ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<Tenant> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM tenants ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Long.class);
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String tenantId) {
        jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class TenantRowMapper implements RowMapper<Tenant> {
        @Override
        public Tenant mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Tenant(
                    rs.getString("tenant_id"),
                    rs.getString("name"),
                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(rs.getString("config"), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}),
                    Tenant.Status.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (Exception e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
