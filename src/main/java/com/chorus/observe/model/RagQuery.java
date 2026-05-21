package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A RAG (Retrieval-Augmented Generation) query within a span.
 */
public record RagQuery(
    @NonNull String queryId,
    @NonNull String spanId,
    @NonNull String runId,
    @NonNull String query,
    @Nullable String retrievedChunks,
    @Nullable String similarityScores,
    long latencyMs,
    @NonNull Map<String, Object> metadata
) {

    public RagQuery {
        Objects.requireNonNull(queryId, "queryId");
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(query, "query");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
