package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An evaluation run executes a dataset against an agent configuration.
 */
public record EvalRun(
    @NonNull String evalRunId,
    @NonNull String datasetId,
    @Nullable String name,
    @NonNull Map<String, Object> agentConfig,
    @NonNull Map<String, Object> scorerConfig,
    int parallelism,
    @NonNull Status status,
    int progressPercent,
    @NonNull Map<String, Object> summaryMetrics,
    @Nullable Instant startedAt,
    @Nullable Instant finishedAt,
    @NonNull Instant createdAt
) {
    public EvalRun {
        Objects.requireNonNull(evalRunId, "evalRunId");
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(status, "status");
        agentConfig = agentConfig != null ? Map.copyOf(agentConfig) : Map.of();
        scorerConfig = scorerConfig != null ? Map.copyOf(scorerConfig) : Map.of();
        summaryMetrics = summaryMetrics != null ? Map.copyOf(summaryMetrics) : Map.of();
        if (parallelism < 1) parallelism = 1;
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
