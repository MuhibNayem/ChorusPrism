package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record Tenant(
    @NonNull String tenantId,
    @NonNull String name,
    @NonNull Map<String, Object> config,
    @NonNull Status status,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public Tenant {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(config);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public enum Status { ACTIVE, SUSPENDED, DELETED }
}
