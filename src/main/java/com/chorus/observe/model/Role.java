package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Role(
    @NonNull String roleId,
    @NonNull String tenantId,
    @NonNull String name,
    @NonNull List<String> permissions,
    @Nullable String description,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public Role {
        Objects.requireNonNull(roleId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(permissions);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
