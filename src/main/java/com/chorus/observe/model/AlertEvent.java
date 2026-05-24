package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Triggered alert event.
 */
public record AlertEvent(
    @NonNull String eventId,
    @NonNull String ruleId,
    @NonNull Instant triggeredAt,
    double value,
    @Nullable Instant resolvedAt,
    boolean notificationSent,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt,
    int retryCount,
    @Nullable Instant nextRetryAt,
    @Nullable String lastError
) {
    public AlertEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(triggeredAt, "triggeredAt");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Backward-compatible constructor for existing callers.
     */
    public AlertEvent(String eventId, String ruleId, Instant triggeredAt, double value,
                      Instant resolvedAt, boolean notificationSent, Map<String, Object> metadata,
                      Instant createdAt) {
        this(eventId, ruleId, triggeredAt, value, resolvedAt, notificationSent, metadata, createdAt, 0, null, null);
    }
}
