package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Result of evaluating a single dataset item within an eval run.
 */
public record EvalResultRecord(
    @NonNull String resultId,
    @NonNull String evalRunId,
    @NonNull String itemId,
    @Nullable String runId,
    @Nullable String spanId,
    @Nullable String actualOutput,
    double score,
    boolean passed,
    long latencyMs,
    @Nullable String reasoning,
    @NonNull Instant createdAt
) {
    public EvalResultRecord {
        Objects.requireNonNull(resultId, "resultId");
        Objects.requireNonNull(evalRunId, "evalRunId");
        Objects.requireNonNull(itemId, "itemId");
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
