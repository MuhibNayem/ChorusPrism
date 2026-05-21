package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A run represents a single agent execution from start to finish.
 */
public record Run(
    @NonNull String runId,
    @NonNull String framework,
    @NonNull String agentId,
    @Nullable String model,
    @NonNull Instant startTime,
    @Nullable Instant endTime,
    @NonNull Status status,
    @NonNull Map<String, String> tags,
    @NonNull Map<String, Object> metadata,
    int totalTokens,
    @NonNull BigDecimal totalCost,
    long latencyMs
) {

    public Run {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(framework, "framework");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(startTime, "startTime");
        status = Objects.requireNonNull(status, "status");
        tags = tags != null ? Map.copyOf(tags) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        totalCost = totalCost != null ? totalCost : BigDecimal.ZERO;
    }

    public enum Status {
        RUNNING,
        SUCCESS,
        ERROR,
        CANCELLED
    }
}
