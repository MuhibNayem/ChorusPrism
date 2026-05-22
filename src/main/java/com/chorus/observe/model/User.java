package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record User(
    @NonNull String userId,
    @NonNull String tenantId,
    @NonNull String email,
    @NonNull String passwordHash,
    @Nullable String displayName,
    @NonNull Status status,
    @Nullable Instant lastLoginAt,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public User {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(email);
        Objects.requireNonNull(passwordHash);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public enum Status { ACTIVE, INACTIVE, LOCKED }
}
