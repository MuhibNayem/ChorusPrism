package com.chorus.observe.persistence;

import com.chorus.observe.model.UserRole;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class UserRoleRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<UserRole> rowMapper = new UserRoleRowMapper();

    public UserRoleRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull UserRole userRole) {
        jdbc.update(
            "INSERT INTO user_roles (user_id, role_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            userRole.userId(), userRole.roleId(), Timestamp.from(userRole.createdAt()));
    }

    public @NonNull List<UserRole> findByUserId(@NonNull String userId) {
        return jdbc.query("SELECT * FROM user_roles WHERE user_id = ?", rowMapper, userId);
    }

    /**
     * Returns all distinct permission strings for a user via a single JOIN.
     * JSON parsing is done in Java so this works with both PostgreSQL and H2.
     */
    public @NonNull List<String> findPermissionsByUserId(@NonNull String userId) {
        List<String> rawJsons = jdbc.query(
            "SELECT r.permissions FROM roles r JOIN user_roles ur ON r.role_id = ur.role_id WHERE ur.user_id = ?",
            (rs, rowNum) -> rs.getString("permissions"),
            userId);
        return rawJsons.stream()
            .flatMap(json -> parseJsonArray(json).stream())
            .distinct()
            .toList();
    }

    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            // Handles JSON arrays like ["runs:read","runs:write"]
            String trimmed = json.trim();
            if (!trimmed.startsWith("[")) return List.of();
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            if (trimmed.isEmpty()) return List.of();
            List<String> result = new java.util.ArrayList<>();
            for (String part : trimmed.split(",")) {
                String val = part.trim();
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    result.add(val.substring(1, val.length() - 1));
                } else {
                    result.add(val);
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    public @NonNull List<UserRole> findByRoleId(@NonNull String roleId) {
        return jdbc.query("SELECT * FROM user_roles WHERE role_id = ?", rowMapper, roleId);
    }

    public void deleteByUserId(@NonNull String userId) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
    }

    public void deleteByUserIdAndRoleId(@NonNull String userId, @NonNull String roleId) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = ? AND role_id = ?", userId, roleId);
    }

    private static final class UserRoleRowMapper implements RowMapper<UserRole> {
        @Override
        public UserRole mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserRole(
                rs.getString("user_id"),
                rs.getString("role_id"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
