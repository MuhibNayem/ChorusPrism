package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A single item within a dataset.
 */
public record DatasetItem(
    @NonNull String itemId,
    @NonNull String datasetId,
    @NonNull String input,
    @Nullable String expectedOutput,
    @NonNull Map<String, Object> metadata,
    @NonNull Map<String, String> tags,
    @NonNull Instant createdAt
) {
    public DatasetItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(input, "input");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        tags = tags != null ? Map.copyOf(tags) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
