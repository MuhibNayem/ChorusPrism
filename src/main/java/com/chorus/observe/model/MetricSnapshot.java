package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A time-series metric snapshot for monitoring dashboards.
 */
public record MetricSnapshot(
    @NonNull String snapshotId,
    @NonNull String metricName,
    double value,
    @NonNull Map<String, String> tags,
    @NonNull Instant timestamp
) {

    public MetricSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(metricName, "metricName");
        Objects.requireNonNull(timestamp, "timestamp");
        tags = tags != null ? Map.copyOf(tags) : Map.of();
    }
}
