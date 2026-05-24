package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ScimToken(
    @Nullable UUID id,
    @NonNull String tenantId,
    @NonNull String name,
    @NonNull String tokenHash,
    @NonNull List<String> scopes,
    @NonNull Instant createdAt,
    @Nullable Instant expiresAt,
    @Nullable Instant revokedAt
) {
    public ScimToken {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(tokenHash);
        Objects.requireNonNull(scopes);
        Objects.requireNonNull(createdAt);
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive() {
        return !isExpired() && !isRevoked();
    }
}
