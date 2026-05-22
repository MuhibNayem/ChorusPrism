package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;

public record UserRole(
    @NonNull String userId,
    @NonNull String roleId,
    @NonNull Instant createdAt
) {
    public UserRole {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(roleId);
        Objects.requireNonNull(createdAt);
    }
}
