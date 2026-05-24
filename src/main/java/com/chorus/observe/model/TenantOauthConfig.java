package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TenantOauthConfig(
    @Nullable UUID id,
    @NonNull String tenantId,
    @NonNull String providerName,
    @NonNull String clientId,
    @NonNull String clientSecret,
    @NonNull String issuerUri,
    @NonNull List<String> scopes,
    @NonNull String defaultRole,
    boolean enabled,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public TenantOauthConfig {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(providerName);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(clientSecret);
        Objects.requireNonNull(issuerUri);
        Objects.requireNonNull(scopes);
        Objects.requireNonNull(defaultRole);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
