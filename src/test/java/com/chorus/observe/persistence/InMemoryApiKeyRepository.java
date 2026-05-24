package com.chorus.observe.persistence;

import com.chorus.observe.model.ApiKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.*;

/**
 * In-memory fake for ApiKeyRepository. Thread-safe for single-threaded tests.
 */
public class InMemoryApiKeyRepository extends ApiKeyRepository {
    private final Map<String, ApiKey> store = new HashMap<>();

    public InMemoryApiKeyRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(@NonNull ApiKey apiKey) {
        store.put(apiKey.keyHash(), apiKey);
    }

    @Override
    public @NonNull Optional<ApiKey> findByKeyHash(@NonNull String keyHash) {
        return Optional.ofNullable(store.get(keyHash));
    }

    @Override
    public @NonNull List<ApiKey> findByTenant(@NonNull String tenantId) {
        return store.values().stream()
            .filter(k -> k.tenantId().equals(tenantId))
            .toList();
    }

    @Override
    public void updateLastUsed(@NonNull String keyHash, @NonNull Instant lastUsedAt) {
        findByKeyHash(keyHash).ifPresent(k -> {
            store.put(keyHash, new ApiKey(
                k.keyHash(), k.tenantId(), k.userId(), k.name(), k.scopes(),
                k.expiresAt(), lastUsedAt, k.createdAt(), k.revokedAt()
            ));
        });
    }

    @Override
    public void revoke(@NonNull String keyHash, @NonNull Instant revokedAt) {
        findByKeyHash(keyHash).ifPresent(k -> {
            store.put(keyHash, new ApiKey(
                k.keyHash(), k.tenantId(), k.userId(), k.name(), k.scopes(),
                k.expiresAt(), k.lastUsedAt(), k.createdAt(), revokedAt
            ));
        });
    }
}
