package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * A single run within an N-run evaluation. Stores the result of one repetition.
 */
public record EvalResultRun(
    @NonNull String resultRunId,
    @NonNull String resultId,
    int runNumber,
    double score,
    boolean passed,
    @Nullable String actualOutput,
    @Nullable String reasoning,
    long latencyMs,
    @NonNull Instant createdAt
) {
    public EvalResultRun {
        Objects.requireNonNull(resultRunId, "resultRunId");
        Objects.requireNonNull(resultId, "resultId");
        if (runNumber < 1) throw new IllegalArgumentException("runNumber must be >= 1");
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
