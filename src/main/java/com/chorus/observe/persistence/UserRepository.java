package com.chorus.observe.persistence;

import com.chorus.observe.model.User;
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

public class UserRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<User> rowMapper = new UserRowMapper();

    public UserRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull User user) {
        String sql = """
            INSERT INTO users (user_id, tenant_id, email, password_hash, display_name, status, last_login_at, auth_source, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                email = EXCLUDED.email,
                password_hash = EXCLUDED.password_hash,
                display_name = EXCLUDED.display_name,
                status = EXCLUDED.status,
                last_login_at = EXCLUDED.last_login_at,
                auth_source = EXCLUDED.auth_source,
                updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            user.userId(), user.tenantId(), user.email(), user.passwordHash(),
            user.displayName(), user.status().name(),
            user.lastLoginAt() != null ? Timestamp.from(user.lastLoginAt()) : null,
            user.authSource().name(),
            Timestamp.from(user.createdAt()), Timestamp.from(user.updatedAt()));
    }

    public @NonNull Optional<User> findById(@NonNull String userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM users WHERE user_id = ?", rowMapper, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull Optional<User> findByEmail(@NonNull String tenantId, @NonNull String email) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM users WHERE tenant_id = ? AND email = ?", rowMapper, tenantId, email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull Optional<User> findByEmailIgnoreCase(@NonNull String tenantId, @NonNull String email) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM users WHERE tenant_id = ? AND LOWER(email) = LOWER(?)", rowMapper, tenantId, email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<User> findByTenant(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM users WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public @NonNull List<User> findByTenant(@NonNull String tenantId, int limit, int offset) {
        return jdbc.query("SELECT * FROM users WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
    }

    public long countByTenant(@NonNull String tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE tenant_id = ?", Long.class, tenantId);
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String userId) {
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    private static final class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new User(
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                User.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("last_login_at") != null ? rs.getTimestamp("last_login_at").toInstant() : null,
                User.AuthSource.valueOf(rs.getString("auth_source")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
