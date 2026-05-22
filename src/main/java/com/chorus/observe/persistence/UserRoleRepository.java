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
