package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record Dashboard(
    @NonNull String dashboardId,
    @NonNull String tenantId,
    @NonNull String userId,
    @NonNull String name,
    @NonNull String description,
    @NonNull Map<String, Object> layout,
    boolean enabled,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public Dashboard {
        Objects.requireNonNull(dashboardId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        Objects.requireNonNull(layout);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
