package com.chorus.observe.export;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

/**
 * Parquet schema record for metric snapshot exports.
 */
public record MetricExportRecord(
    @NonNull String snapshotId,
    @NonNull String tenantId,
    @NonNull String metricName,
    double value,
    @NonNull String tagsJson,
    @NonNull Instant timestamp
) {}
