package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record RetentionPolicy(
    @NonNull String policyId,
    @NonNull String tenantId,
    @NonNull String name,
    @NonNull String resourceType,
    int retentionDays,
    boolean archiveEnabled,
    @Nullable String archiveLocation,
    boolean enabled,
    @Nullable Instant lastRunAt,
    long lastRunDeleted,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public RetentionPolicy {
        Objects.requireNonNull(policyId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
