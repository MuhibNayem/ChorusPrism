package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * A single evaluation result applied to a run.
 */
public record RunEvaluation(
    @NonNull String evaluationId,
    @NonNull String runId,
    @NonNull String evaluatorId,
    double score,
    boolean passed,
    @Nullable Map<String, Object> details
) {

    public RunEvaluation {
        Objects.requireNonNull(evaluationId, "evaluationId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(evaluatorId, "evaluatorId");
        details = details != null ? Map.copyOf(details) : null;
    }
}
