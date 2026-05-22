package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Record of a single turn within a multi-turn test run.
 */
public record MultiTurnTurn(
    @NonNull String turnId,
    @NonNull String runId,
    int turnIndex,
    @NonNull String role,
    @NonNull String inputMessage,
    @Nullable String agentOutput,
    @NonNull List<String> expectedKeywords,
    @NonNull List<String> matchedKeywords,
    double score,
    boolean passed,
    long latencyMs,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt
) {
    public MultiTurnTurn {
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(runId, "runId");
        expectedKeywords = expectedKeywords != null ? List.copyOf(expectedKeywords) : List.of();
        matchedKeywords = matchedKeywords != null ? List.copyOf(matchedKeywords) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
