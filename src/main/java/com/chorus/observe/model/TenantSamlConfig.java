package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    @NonNull List<RoleMapping> roleMappings,
    @NonNull List<String> allowedDomains,
    @NonNull Map<String, String> attributeMappings,
    @Nullable String idpCertPem,
    @Nullable String idpMetadataXml,
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
        roleMappings = roleMappings != null ? List.copyOf(roleMappings) : List.of();
        allowedDomains = allowedDomains != null ? List.copyOf(allowedDomains) : List.of();
        attributeMappings = attributeMappings != null ? Map.copyOf(attributeMappings) : Map.of();
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
