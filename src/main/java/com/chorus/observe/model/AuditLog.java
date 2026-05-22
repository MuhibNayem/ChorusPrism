package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AuditLog(
    @NonNull String logId,
    @NonNull String tenantId,
    @Nullable String userId,
    @NonNull String action,
    @NonNull String resourceType,
    @Nullable String resourceId,
    @Nullable Map<String, Object> oldValue,
    @Nullable Map<String, Object> newValue,
    @Nullable String ipAddress,
    @Nullable String userAgent,
    boolean success,
    @NonNull Map<String, Object> details,
    @NonNull Instant createdAt
) {
    public AuditLog {
        Objects.requireNonNull(logId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(action);
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(details);
        Objects.requireNonNull(createdAt);
    }
}
