package com.chorus.observe.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Stores revoked JWT JTIs so that logged-out or password-changed tokens
 * cannot be reused until they naturally expire.
 */
public class TokenBlacklistRepository {

    private final JdbcTemplate jdbc;

    public TokenBlacklistRepository(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void revoke(@NonNull String jti, @NonNull String tenantId, @NonNull Instant expiresAt) {
        jdbc.update(
            "INSERT INTO revoked_tokens (jti, tenant_id, expires_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            jti, tenantId, Timestamp.from(expiresAt));
    }

    public boolean isRevoked(@NonNull String jti) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM revoked_tokens WHERE jti = ?", Integer.class, jti);
        return count != null && count > 0;
    }

    /** Purge tokens that have already expired — call from a scheduled task. */
    public void purgeExpired() {
        jdbc.update("DELETE FROM revoked_tokens WHERE expires_at < NOW()");
    }
}
