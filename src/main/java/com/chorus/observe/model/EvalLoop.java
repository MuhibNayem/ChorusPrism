package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Persisted configuration for continuous evaluation on production agents.
 */
public record EvalLoop(
    @NonNull String loopId,
    @NonNull String agentId,
    @NonNull String evaluatorId,
    int samplingRate,
    double alertThreshold,
    @NonNull String status,
    @NonNull Instant createdAt,
    @Nullable Instant lastRunAt
) {
    public EvalLoop {
        Objects.requireNonNull(loopId, "loopId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(evaluatorId, "evaluatorId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (samplingRate < 0 || samplingRate > 100) {
            throw new IllegalArgumentException("samplingRate must be between 0 and 100");
        }
        if (alertThreshold < 0.0 || alertThreshold > 1.0) {
            throw new IllegalArgumentException("alertThreshold must be between 0.0 and 1.0");
        }
    }
}
