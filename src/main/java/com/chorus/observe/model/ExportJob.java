package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ExportJob(
    @NonNull String jobId,
    @NonNull String tenantId,
    @NonNull String userId,
    @NonNull String name,
    @NonNull String resourceType,
    @NonNull Map<String, Object> queryFilter,
    @NonNull Format format,
    @NonNull Destination destination,
    @Nullable String destinationPath,
    @NonNull Status status,
    @Nullable Long totalRecords,
    @Nullable Long fileSizeBytes,
    @Nullable String errorMessage,
    @Nullable Instant startedAt,
    @Nullable Instant finishedAt,
    @NonNull Instant createdAt
) {
    public ExportJob {
        Objects.requireNonNull(jobId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(queryFilter);
        Objects.requireNonNull(format);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
    }

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }
    public enum Format { JSON, CSV, PARQUET }
    public enum Destination { FILE, S3 }
}
