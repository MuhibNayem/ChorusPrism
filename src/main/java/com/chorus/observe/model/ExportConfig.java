package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record ExportConfig(
    @NonNull String configId,
    @NonNull String tenantId,
    @NonNull DestinationType destinationType,
    @Nullable String endpointUrl,
    @Nullable String region,
    @Nullable String bucketName,
    @Nullable String accessKeyId,
    @Nullable String secretAccessKey,
    @NonNull String pathPrefix,
    boolean enabled,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public ExportConfig {
        Objects.requireNonNull(configId, "configId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(destinationType, "destinationType");
        pathPrefix = pathPrefix != null ? pathPrefix : "";
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public enum DestinationType { FILE, S3 }
}
