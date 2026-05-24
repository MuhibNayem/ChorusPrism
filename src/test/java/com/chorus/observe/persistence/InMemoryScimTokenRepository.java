package com.chorus.observe.persistence;

import com.chorus.observe.model.ScimToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.*;

public class InMemoryScimTokenRepository extends ScimTokenRepository {
    private final Map<String, ScimToken> store = new HashMap<>();

    public InMemoryScimTokenRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(@NonNull ScimToken token) {
        store.put(token.tokenHash(), token);
    }

    @Override
    public @NonNull Optional<ScimToken> findById(@NonNull UUID id) {
        return store.values().stream()
            .filter(t -> t.id() != null && t.id().equals(id))
            .findFirst();
    }

    @Override
    public @NonNull Optional<ScimToken> findByTokenHash(@NonNull String tokenHash) {
        return Optional.ofNullable(store.get(tokenHash));
    }

    @Override
    public @NonNull List<ScimToken> findByTenantId(@NonNull String tenantId) {
        return store.values().stream()
            .filter(t -> t.tenantId().equals(tenantId))
            .toList();
    }

    @Override
    public void revokeById(@NonNull UUID id, @NonNull Instant revokedAt) {
        findById(id).ifPresent(t -> {
            store.put(t.tokenHash(), new ScimToken(
                t.id(), t.tenantId(), t.name(), t.tokenHash(), t.scopes(),
                t.createdAt(), t.expiresAt(), revokedAt));
        });
    }

    @Override
    public void deleteById(@NonNull UUID id) {
        findById(id).ifPresent(t -> store.remove(t.tokenHash()));
    }
}
