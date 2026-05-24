package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantSamlConfig(
    @Nullable UUID id,
    @NonNull String tenantId,
    @NonNull String providerName,
    @NonNull String entityId,
    @NonNull String signOnUrl,
    @NonNull String signingCertThumbprint,
    @Nullable String metadataUrl,
    @NonNull String acsUrl,
    @NonNull String defaultRole,
    boolean enabled,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public TenantSamlConfig {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(providerName);
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(signOnUrl);
        Objects.requireNonNull(signingCertThumbprint);
        Objects.requireNonNull(acsUrl);
        Objects.requireNonNull(defaultRole);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
