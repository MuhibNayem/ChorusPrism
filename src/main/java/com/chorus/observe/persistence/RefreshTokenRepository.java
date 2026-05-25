package com.chorus.observe.persistence;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Manages refresh tokens for the access-token rotation flow.
 * Each refresh token is stored as a SHA-256 hash; the raw value is returned
 * once to the client and never stored.
 */
public class RefreshTokenRepository {

    private final JdbcTemplate jdbc;

    public RefreshTokenRepository(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void save(@NonNull String tokenHash, @NonNull String userId, @NonNull String tenantId,
                     @NonNull String jti, @NonNull Instant expiresAt) {
        jdbc.update(
            "INSERT INTO refresh_tokens (token_hash, user_id, tenant_id, jti, expires_at) VALUES (?, ?, ?, ?, ?)",
            tokenHash, userId, tenantId, jti, Timestamp.from(expiresAt));
    }

    public @NonNull Optional<RefreshTokenRecord> findByHash(@NonNull String tokenHash) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT token_hash, user_id, tenant_id, jti, expires_at, revoked_at " +
                "FROM refresh_tokens WHERE token_hash = ?",
                (rs, rowNum) -> mapRow(rs),
                tokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void revoke(@NonNull String tokenHash) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = NOW() WHERE token_hash = ?", tokenHash);
    }

    public void revokeAllForUser(@NonNull String userId) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL", userId);
    }

    public void purgeExpired() {
        jdbc.update("DELETE FROM refresh_tokens WHERE expires_at < NOW()");
    }

    private RefreshTokenRecord mapRow(ResultSet rs) throws java.sql.SQLException {
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new RefreshTokenRecord(
            rs.getString("token_hash"),
            rs.getString("user_id"),
            rs.getString("tenant_id"),
            rs.getString("jti"),
            rs.getTimestamp("expires_at").toInstant(),
            revokedAt != null ? revokedAt.toInstant() : null
        );
    }

    public record RefreshTokenRecord(
        @NonNull String tokenHash,
        @NonNull String userId,
        @NonNull String tenantId,
        @NonNull String jti,
        @NonNull Instant expiresAt,
        @Nullable Instant revokedAt
    ) {
        public boolean isValid() {
            return revokedAt == null && expiresAt.isAfter(Instant.now());
        }
    }
}
