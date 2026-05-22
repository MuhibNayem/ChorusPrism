package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Embedding vector for a trace run or span, used in cluster analysis.
 */
public record TraceEmbedding(
    @NonNull String embeddingId,
    @NonNull String runId,
    @Nullable String spanId,
    @NonNull String model,
    @NonNull float[] vector,
    @NonNull String textSource,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt
) {
    public TraceEmbedding {
        Objects.requireNonNull(embeddingId, "embeddingId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(textSource, "textSource");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
