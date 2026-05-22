package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a single red team scenario execution.
 */
public record RedTeamResult(
    @NonNull String resultId,
    @NonNull String redTeamRunId,
    @NonNull String scenarioId,
    @Nullable String agentOutput,
    @NonNull Map<String, Object> guardrailResult,
    boolean bypassed,
    RedTeamScenario.Severity severity,
    long latencyMs,
    @NonNull Instant createdAt
) {
    public RedTeamResult {
        Objects.requireNonNull(resultId, "resultId");
        Objects.requireNonNull(redTeamRunId, "redTeamRunId");
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(severity, "severity");
        guardrailResult = guardrailResult != null ? Map.copyOf(guardrailResult) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
