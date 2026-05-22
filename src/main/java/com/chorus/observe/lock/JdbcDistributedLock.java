package com.chorus.observe.lock;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed distributed lock using a database table.
 * <p>
 * Acquisition uses {@code INSERT} with primary-key uniqueness. Release uses
 * conditional {@code DELETE} on lock name + token ID. A background reaper
 * cleans up expired rows.
 * <p>
 * The token_id is persisted in the database so that only the exact acquirer
 * can release the lock, even after expired-lock stealing.
 */
public final class JdbcDistributedLock implements DistributedLock {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcDistributedLock.class);

    private final String lockName;
    private final String ownerId;
    private final JdbcTemplate jdbc;
    private final Duration defaultTtl;
    private final Duration pollInterval;

    public JdbcDistributedLock(
            @NonNull String lockName,
            @NonNull String ownerId,
            @NonNull DataSource dataSource,
            @NonNull Duration defaultTtl,
            @NonNull Duration pollInterval
    ) {
        this.lockName = Objects.requireNonNull(lockName);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
        this.defaultTtl = Objects.requireNonNull(defaultTtl);
        this.pollInterval = Objects.requireNonNull(pollInterval);
    }

    @Override
    public @NonNull Optional<LockToken> tryLock() {
        return tryAcquire();
    }

    @Override
    public @NonNull Optional<LockToken> tryLock(@NonNull Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Optional<LockToken> acquired = tryAcquire();
            if (acquired.isPresent()) {
                return acquired;
            }
            Thread.sleep(pollInterval.toMillis());
        }
        return Optional.empty();
    }

    private @NonNull Optional<LockToken> tryAcquire() {
        String tokenId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(defaultTtl);
        try {
            jdbc.update(
                "INSERT INTO distributed_locks (lock_name, owner_id, token_id, acquired_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                lockName, ownerId, tokenId, Timestamp.from(now), Timestamp.from(expiresAt));
            LOG.debug("Lock acquired: {} by {} (token={})", lockName, ownerId, tokenId);
            return Optional.of(new LockToken(lockName, ownerId, tokenId));
        } catch (DuplicateKeyException e) {
            // Lock already held; try to steal if expired
            return tryStealExpired(tokenId);
        }
    }

    private @NonNull Optional<LockToken> tryStealExpired(@NonNull String tokenId) {
        int stolen = jdbc.update(
            "UPDATE distributed_locks SET owner_id = ?, token_id = ?, acquired_at = ?, expires_at = ? WHERE lock_name = ? AND expires_at < ?",
            ownerId, tokenId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now().plus(defaultTtl)),
            lockName, Timestamp.from(Instant.now()));
        if (stolen > 0) {
            LOG.warn("Stole expired lock: {} (new owner={}, token={})", lockName, ownerId, tokenId);
            return Optional.of(new LockToken(lockName, ownerId, tokenId));
        }
        return Optional.empty();
    }

    @Override
    public boolean unlock(@NonNull LockToken token) {
        Objects.requireNonNull(token);
        if (!token.lockName().equals(lockName) || !token.ownerId().equals(ownerId)) {
            LOG.warn("Unlock rejected: token owner {} does not match lock owner {}", token.ownerId(), ownerId);
            return false;
        }
        int deleted = jdbc.update(
            "DELETE FROM distributed_locks WHERE lock_name = ? AND token_id = ?",
            lockName, token.tokenId());
        if (deleted > 0) {
            LOG.debug("Lock released: {} by {} (token={})", lockName, ownerId, token.tokenId());
            return true;
        }
        LOG.warn("Unlock failed: lock {} not found or token mismatch (token={})", lockName, token.tokenId());
        return false;
    }

    @Override
    public boolean renew(@NonNull LockToken token, @NonNull Duration extension) {
        Objects.requireNonNull(token);
        if (!token.lockName().equals(lockName) || !token.ownerId().equals(ownerId)) {
            return false;
        }
        Instant newExpiry = Instant.now().plus(extension);
        int updated = jdbc.update(
            "UPDATE distributed_locks SET expires_at = ? WHERE lock_name = ? AND token_id = ?",
            Timestamp.from(newExpiry), lockName, token.tokenId());
        return updated > 0;
    }

    @Override
    public boolean forceUnlock() {
        int deleted = jdbc.update("DELETE FROM distributed_locks WHERE lock_name = ?", lockName);
        if (deleted > 0) {
            LOG.warn("Force-unlocked: {}", lockName);
        }
        return deleted > 0;
    }
}
