package com.chorus.observe.export;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Parquet schema record for span exports.
 * Designed for compatibility with pandas, DuckDB, and Athena.
 */
public record SpanExportRecord(
    @NonNull String spanId,
    @NonNull String runId,
    @Nullable String parentSpanId,
    @NonNull String spanName,
    @NonNull String kind,
    @NonNull Instant startTime,
    @Nullable Instant endTime,
    @NonNull String attributesJson,
    @NonNull String eventsJson,
    @NonNull String status,
    @Nullable String spanType,
    @Nullable Instant firstTokenAt,
    @NonNull String tenantId
) {}
