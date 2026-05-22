package com.chorus.observe.lock;

import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for named distributed locks.
 * <p>
 * Each JVM instance gets a unique {@code ownerId} (hostname + UUID) so locks
 * are scoped to the process. The registry caches {@link DistributedLock} instances
 * per lock name to avoid recreating them.
 */
public final class DistributedLockRegistry {

    private final DataSource dataSource;
    private final Duration defaultTtl;
    private final Duration pollInterval;
    private final String ownerId;
    private final ConcurrentHashMap<String, DistributedLock> cache = new ConcurrentHashMap<>();

    public DistributedLockRegistry(@NonNull DataSource dataSource, @NonNull Duration defaultTtl, @NonNull Duration pollInterval) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.defaultTtl = Objects.requireNonNull(defaultTtl);
        this.pollInterval = Objects.requireNonNull(pollInterval);
        this.ownerId = resolveOwnerId();
    }

    /**
     * Get or create a lock with the given name.
     */
    public @NonNull DistributedLock getLock(@NonNull String lockName) {
        return cache.computeIfAbsent(lockName, name ->
            new JdbcDistributedLock(name, ownerId, dataSource, defaultTtl, pollInterval));
    }

    private static @NonNull String resolveOwnerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
