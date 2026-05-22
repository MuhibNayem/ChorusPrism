package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A dataset is a collection of test cases for evaluation.
 */
public record Dataset(
    @NonNull String datasetId,
    @NonNull String name,
    @Nullable String description,
    @NonNull Map<String, String> tags,
    @NonNull String source,
    @NonNull Map<String, Object> splitConfig,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public Dataset {
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        tags = tags != null ? Map.copyOf(tags) : Map.of();
        splitConfig = splitConfig != null ? Map.copyOf(splitConfig) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }
}
