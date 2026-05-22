package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Cluster of semantically similar traces.
 */
public record TraceCluster(
    @NonNull String clusterId,
    @NonNull String label,
    @Nullable String description,
    int runCount,
    @Nullable Double avgScore,
    @Nullable BigDecimal avgCost,
    @NonNull Instant periodStart,
    @NonNull Instant periodEnd,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt
) {
    public TraceCluster {
        Objects.requireNonNull(clusterId, "clusterId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
