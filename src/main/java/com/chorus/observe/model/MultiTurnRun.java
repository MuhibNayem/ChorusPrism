package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Result of executing a multi-turn scenario against an agent.
 */
public record MultiTurnRun(
    @NonNull String runId,
    @NonNull String scenarioId,
    @NonNull Map<String, Object> agentConfig,
    @NonNull Status status,
    int totalTurns,
    int passedTurns,
    int failedTurns,
    @Nullable Double finalScore,
    @NonNull Map<String, Object> summaryMetrics,
    @Nullable Instant startedAt,
    @Nullable Instant finishedAt,
    @NonNull Instant createdAt
) {
    public MultiTurnRun {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(scenarioId, "scenarioId");
        agentConfig = agentConfig != null ? Map.copyOf(agentConfig) : Map.of();
        status = status != null ? status : Status.PENDING;
        summaryMetrics = summaryMetrics != null ? Map.copyOf(summaryMetrics) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }
}
