package com.chorus.observe.service;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency key tracker for POST endpoints.
 * Keys expire after 24 hours. Production deployments should use Redis.
 */
public class IdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyService.class);
    private static final long TTL_HOURS = 24;

    private final Map<String, IdempotencyEntry> store = new ConcurrentHashMap<>();

    /**
     * Check if a key has been seen. If new, record it.
     *
     * @return null if the key is new; the existing resource id if already processed
     */
    public @Nullable String checkOrRecord(@NonNull String key, @NonNull String resourceId) {
        cleanup();
        IdempotencyEntry existing = store.putIfAbsent(key, new IdempotencyEntry(resourceId, Instant.now()));
        if (existing != null) {
            LOG.info("Idempotency key {} already processed -> {}", key, existing.resourceId());
            return existing.resourceId();
        }
        return null;
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(TTL_HOURS * 3600);
        store.values().removeIf(e -> e.createdAt().isBefore(cutoff));
    }

    private record IdempotencyEntry(@NonNull String resourceId, @NonNull Instant createdAt) {}
}
