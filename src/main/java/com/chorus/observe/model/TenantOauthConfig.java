package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    @NonNull List<RoleMapping> roleMappings,
    @NonNull List<String> allowedDomains,
    @NonNull Map<String, String> attributeMappings,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public TenantOauthConfig {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(providerName);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(clientSecret);
        Objects.requireNonNull(issuerUri);
        scopes = scopes != null ? List.copyOf(scopes) : List.of();
        Objects.requireNonNull(defaultRole);
        roleMappings = roleMappings != null ? List.copyOf(roleMappings) : List.of();
        allowedDomains = allowedDomains != null ? List.copyOf(allowedDomains) : List.of();
        attributeMappings = attributeMappings != null ? Map.copyOf(attributeMappings) : Map.of();
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
