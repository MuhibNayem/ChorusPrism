package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ApiKey(
    @NonNull String keyHash,
    @NonNull String tenantId,
    @Nullable String userId,
    @NonNull String name,
    @NonNull List<String> scopes,
    @Nullable Instant expiresAt,
    @Nullable Instant lastUsedAt,
    @NonNull Instant createdAt,
    @Nullable Instant revokedAt,
    @Nullable String keyPrefix
) {
    public ApiKey {
        Objects.requireNonNull(keyHash);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(scopes);
        Objects.requireNonNull(createdAt);
    }

    /** Backward-compat constructor without keyPrefix (for existing callers). */
    public ApiKey(String keyHash, String tenantId, String userId, String name,
                  List<String> scopes, Instant expiresAt, Instant lastUsedAt,
                  Instant createdAt, Instant revokedAt) {
        this(keyHash, tenantId, userId, name, scopes, expiresAt, lastUsedAt, createdAt, revokedAt, null);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean hasScope(@NonNull String scope) {
        return scopes.contains(scope) || scopes.contains("admin");
    }
}
