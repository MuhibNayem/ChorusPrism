package com.chorus.observe.security;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * DB-backed per-email brute-force protection for the login endpoint.
 * <p>
 * After {@value MAX_ATTEMPTS} consecutive failures the account is locked
 * for {@value LOCKOUT_MINUTES} minutes. A successful login resets the counter.
 */
public class LoginAttemptService {

    static final int MAX_ATTEMPTS    = 5;
    static final int LOCKOUT_MINUTES = 15;
    static final int WINDOW_MINUTES  = 15;

    private final JdbcTemplate jdbc;

    public LoginAttemptService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public boolean isLocked(String email, @Nullable String tenantId) {
        String key = normalize(email);
        String tid = tid(tenantId);
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_attempts " +
                "WHERE identifier = ? AND tenant_id = ? " +
                "AND locked_until IS NOT NULL AND locked_until > ?",
                Integer.class, key, tid, Timestamp.from(Instant.now()));
            return count != null && count > 0;
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    public void recordFailure(String email, @Nullable String tenantId) {
        String key = normalize(email);
        String tid = tid(tenantId);
        Instant now = Instant.now();
        Instant windowCutoff = now.minusSeconds((long) WINDOW_MINUTES * 60);

        // Check if existing row is in the current window
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM login_attempts WHERE identifier = ? AND tenant_id = ?",
            Integer.class, key, tid);

        if (existing == null || existing == 0) {
            jdbc.update(
                "INSERT INTO login_attempts (identifier, tenant_id, attempt_count, window_start, last_attempt_at) " +
                "VALUES (?, ?, 1, ?, ?)",
                key, tid, Timestamp.from(now), Timestamp.from(now));
        } else {
            // Reset window if it's stale, then increment
            jdbc.update("""
                UPDATE login_attempts SET
                    attempt_count   = CASE WHEN window_start < ? THEN 1 ELSE attempt_count + 1 END,
                    window_start    = CASE WHEN window_start < ? THEN ? ELSE window_start END,
                    locked_until    = CASE
                                        WHEN (CASE WHEN window_start < ? THEN 1 ELSE attempt_count + 1 END) >= ?
                                        THEN ?
                                        ELSE NULL
                                      END,
                    last_attempt_at = ?
                WHERE identifier = ? AND tenant_id = ?
                """,
                Timestamp.from(windowCutoff), Timestamp.from(windowCutoff), Timestamp.from(now),
                Timestamp.from(windowCutoff), MAX_ATTEMPTS,
                Timestamp.from(now.plusSeconds((long) LOCKOUT_MINUTES * 60)),
                Timestamp.from(now),
                key, tid);
        }
    }

    public void recordSuccess(String email, @Nullable String tenantId) {
        jdbc.update(
            "DELETE FROM login_attempts WHERE identifier = ? AND tenant_id = ?",
            normalize(email), tid(tenantId));
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpired() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        jdbc.update(
            "DELETE FROM login_attempts WHERE last_attempt_at < ? " +
            "AND (locked_until IS NULL OR locked_until < ?)",
            Timestamp.from(cutoff), Timestamp.from(Instant.now()));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.toLowerCase().trim();
    }

    private static String tid(@Nullable String tenantId) {
        return tenantId == null ? "" : tenantId;
    }
}
