package com.chorus.observe.persistence;

import com.chorus.observe.model.Role;
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
import java.util.Objects;
import java.util.Optional;

public class RoleRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Role> rowMapper;

    public RoleRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RoleRowMapper(mapper);
    }

    public void save(@NonNull Role role) {
        String sql = """
            INSERT INTO roles (role_id, tenant_id, name, permissions, description, created_at, updated_at)
            VALUES (?, ?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (role_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                permissions = EXCLUDED.permissions,
                description = EXCLUDED.description,
                updated_at = EXCLUDED.updated_at
            """;
        try {
            jdbc.update(sql,
                role.roleId(), role.tenantId(), role.name(),
                mapper.writeValueAsString(role.permissions()),
                role.description(),
                Timestamp.from(role.createdAt()), Timestamp.from(role.updatedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize permissions", e);
        }
    }

    public @NonNull Optional<Role> findById(@NonNull String roleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM roles WHERE role_id = ?", rowMapper, roleId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Role> findByTenant(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM roles WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public @NonNull List<Role> findByTenant(@NonNull String tenantId, int limit, int offset) {
        return jdbc.query("SELECT * FROM roles WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
    }

    public @NonNull Optional<Role> findByTenantIdAndName(@NonNull String tenantId, @NonNull String name) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM roles WHERE tenant_id = ? AND name = ?", rowMapper, tenantId, name));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public long countByTenant(@NonNull String tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE tenant_id = ?", Long.class, tenantId);
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String roleId) {
        jdbc.update("DELETE FROM roles WHERE role_id = ?", roleId);
    }

    private static final class RoleRowMapper implements RowMapper<Role> {
        private final ObjectMapper mapper;

        RoleRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public Role mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Role(
                    rs.getString("role_id"),
                    rs.getString("tenant_id"),
                    rs.getString("name"),
                    mapper.readValue(rs.getString("permissions"), new TypeReference<List<String>>() {}),
                    rs.getString("description"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
