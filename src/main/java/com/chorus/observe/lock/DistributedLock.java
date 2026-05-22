package com.chorus.observe.lock;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Optional;

/**
 * Enterprise-grade distributed lock with lease TTL and owner identity.
 * <p>
 * Implementation guarantees:
 * <ul>
 *   <li>Mutual exclusion across JVMs and nodes</li>
 *   <li>Lease TTL with automatic expiry on crash (deadlock prevention)</li>
 *   <li>Owner-scoped release (only the acquirer can release)</li>
 *   <li>Non-blocking {@code tryLock()} with configurable timeout</li>
 * </ul>
 */
public interface DistributedLock {

    /**
     * Attempt to acquire the lock with the default lease TTL.
     *
     * @return lock token if acquired; empty if lock is held by another owner
     */
    @NonNull Optional<LockToken> tryLock();

    /**
     * Attempt to acquire the lock, waiting up to the specified timeout.
     *
     * @param timeout max time to wait
     * @return lock token if acquired; empty if timeout elapsed
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    @NonNull Optional<LockToken> tryLock(@NonNull Duration timeout) throws InterruptedException;

    /**
     * Release the lock. Must be called with the token returned by {@code tryLock}.
     *
     * @param token the lock token
     * @return true if the lock was released; false if token was invalid or expired
     */
    boolean unlock(@NonNull LockToken token);

    /**
     * Renew the lease of a held lock, extending its expiry time.
     *
     * @param token the lock token
     * @param extension the duration to extend
     * @return true if renewal succeeded
     */
    boolean renew(@NonNull LockToken token, @NonNull Duration extension);

    /**
     * Force-release a lock regardless of owner. Use with caution (e.g., admin recovery).
     *
     * @return true if a lock was force-released
     */
    boolean forceUnlock();

    /**
     * Immutable token representing a held lock.
     */
    record LockToken(@NonNull String lockName, @NonNull String ownerId, @NonNull String tokenId) {}
}
