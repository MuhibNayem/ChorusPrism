package com.chorus.observe.persistence;

import com.chorus.observe.model.Dashboard;
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

public class DashboardRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Dashboard> rowMapper;

    public DashboardRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new DashboardRowMapper(mapper);
    }

    public void save(@NonNull Dashboard dashboard) {
        String sql = """
            INSERT INTO dashboards (dashboard_id, tenant_id, user_id, name, description, layout, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (dashboard_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                user_id = EXCLUDED.user_id,
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                layout = EXCLUDED.layout,
                enabled = EXCLUDED.enabled,
                updated_at = EXCLUDED.updated_at
            """;
        try {
            jdbc.update(sql,
                dashboard.dashboardId(), dashboard.tenantId(), dashboard.userId(),
                dashboard.name(), dashboard.description(),
                mapper.writeValueAsString(dashboard.layout()),
                dashboard.enabled(),
                Timestamp.from(dashboard.createdAt()),
                Timestamp.from(dashboard.updatedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize layout", e);
        }
    }

    public @NonNull Optional<Dashboard> findById(@NonNull String dashboardId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM dashboards WHERE dashboard_id = ?", rowMapper, dashboardId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Dashboard> findByTenant(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM dashboards WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public @NonNull List<Dashboard> findByTenant(@NonNull String tenantId, int limit, int offset) {
        return jdbc.query("SELECT * FROM dashboards WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
    }

    public long countByTenant(@NonNull String tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM dashboards WHERE tenant_id = ?", Long.class, tenantId);
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String dashboardId) {
        jdbc.update("DELETE FROM dashboards WHERE dashboard_id = ?", dashboardId);
    }

    private static final class DashboardRowMapper implements RowMapper<Dashboard> {
        private final ObjectMapper mapper;

        DashboardRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public Dashboard mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Dashboard(
                    rs.getString("dashboard_id"),
                    rs.getString("tenant_id"),
                    rs.getString("user_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    mapper.readValue(rs.getString("layout"), new TypeReference<Map<String, Object>>() {}),
                    rs.getBoolean("enabled"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
