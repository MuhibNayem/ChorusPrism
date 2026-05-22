package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Telemetry event from a guardrail evaluation.
 */
public record GuardrailTelemetry(
    @NonNull String telemetryId,
    @Nullable String runId,
    @NonNull String guardrailName,
    int tier,
    @NonNull String action,
    @Nullable Double confidence,
    long latencyMs,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt
) {
    public GuardrailTelemetry {
        Objects.requireNonNull(telemetryId, "telemetryId");
        Objects.requireNonNull(guardrailName, "guardrailName");
        Objects.requireNonNull(action, "action");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
