package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A replay run forks from a checkpoint with optional state overrides.
 */
public record ReplayRun(
    @NonNull String replayRunId,
    @NonNull String originalRunId,
    @Nullable String fromCheckpointId,
    @NonNull Map<String, Object> stateOverrides,
    @NonNull Status status,
    @Nullable Instant startedAt,
    @Nullable Instant finishedAt,
    @NonNull Instant createdAt
) {
    public ReplayRun {
        Objects.requireNonNull(replayRunId, "replayRunId");
        Objects.requireNonNull(originalRunId, "originalRunId");
        Objects.requireNonNull(status, "status");
        stateOverrides = stateOverrides != null ? Map.copyOf(stateOverrides) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
