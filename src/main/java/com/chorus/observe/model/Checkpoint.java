package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checkpoint captured during agent execution for time-travel debugging.
 */
public record Checkpoint(
    @NonNull String checkpointId,
    @NonNull String runId,
    int sequence,
    @NonNull Map<String, Object> stateSnapshot,
    @NonNull List<String> nextNodes,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt
) {
    public Checkpoint {
        Objects.requireNonNull(checkpointId, "checkpointId");
        Objects.requireNonNull(runId, "runId");
        stateSnapshot = stateSnapshot != null ? Map.copyOf(stateSnapshot) : Map.of();
        nextNodes = nextNodes != null ? List.copyOf(nextNodes) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
