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
    @NonNull Instant createdAt
) {
    public AlertEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(triggeredAt, "triggeredAt");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
