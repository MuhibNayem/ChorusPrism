package com.chorus.observe.persistence;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class PasswordResetRepository {

    private final JdbcTemplate jdbc;

    public PasswordResetRepository(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void save(@NonNull String tokenHash, @NonNull String userId,
                     @NonNull String tenantId, @NonNull Instant expiresAt) {
        // Invalidate any existing unused tokens for this user
        jdbc.update("UPDATE password_reset_tokens SET used_at = NOW() " +
                    "WHERE user_id = ? AND used_at IS NULL", userId);
        jdbc.update(
            "INSERT INTO password_reset_tokens (token_hash, user_id, tenant_id, expires_at) VALUES (?, ?, ?, ?)",
            tokenHash, userId, tenantId, Timestamp.from(expiresAt));
    }

    public @NonNull Optional<ResetTokenRecord> findByHash(@NonNull String tokenHash) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT token_hash, user_id, tenant_id, expires_at, used_at " +
                "FROM password_reset_tokens WHERE token_hash = ?",
                (rs, rowNum) -> {
                    Timestamp usedAt = rs.getTimestamp("used_at");
                    return new ResetTokenRecord(
                        rs.getString("token_hash"),
                        rs.getString("user_id"),
                        rs.getString("tenant_id"),
                        rs.getTimestamp("expires_at").toInstant(),
                        usedAt != null ? usedAt.toInstant() : null
                    );
                },
                tokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void markUsed(@NonNull String tokenHash) {
        jdbc.update("UPDATE password_reset_tokens SET used_at = NOW() WHERE token_hash = ?", tokenHash);
    }

    public void purgeExpired() {
        jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at < NOW()");
    }

    public record ResetTokenRecord(
        @NonNull String tokenHash,
        @NonNull String userId,
        @NonNull String tenantId,
        @NonNull Instant expiresAt,
        @Nullable Instant usedAt
    ) {
        public boolean isValid() {
            return usedAt == null && expiresAt.isAfter(Instant.now());
        }
    }
}
