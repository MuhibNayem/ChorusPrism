package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A red team run executes adversarial scenarios against an agent.
 */
public record RedTeamRun(
    @NonNull String redTeamRunId,
    @NonNull Map<String, Object> agentConfig,
    @NonNull Status status,
    int totalScenarios,
    int bypassedCount,
    int blockedCount,
    int progressPercent,
    @NonNull Map<String, Object> summaryMetrics,
    @Nullable Instant startedAt,
    @Nullable Instant finishedAt,
    @NonNull Instant createdAt
) {
    public RedTeamRun {
        Objects.requireNonNull(redTeamRunId, "redTeamRunId");
        Objects.requireNonNull(status, "status");
        agentConfig = agentConfig != null ? Map.copyOf(agentConfig) : Map.of();
        summaryMetrics = summaryMetrics != null ? Map.copyOf(summaryMetrics) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

}
