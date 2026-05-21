package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single node in the causal provenance DAG for a Chorus Engine run.
 * Records why a decision was made and which decisions caused it.
 */
public record ProvenanceEntry(
    @NonNull String entryId,
    @NonNull String runId,
    @NonNull String agentId,
    @NonNull String decisionType,
    @Nullable String inputState,
    @Nullable String reasoning,
    @Nullable String output,
    @NonNull List<String> parentIds,
    @NonNull Instant timestamp,
    @NonNull Map<String, Object> metadata
) {

    public ProvenanceEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(decisionType, "decisionType");
        parentIds = parentIds != null ? List.copyOf(parentIds) : List.of();
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
