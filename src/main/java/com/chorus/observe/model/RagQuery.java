package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * A RAG (Retrieval-Augmented Generation) query captured within a span.
 * <p>
 * Scoring fields ({@code contextPrecision}, {@code contextRecall},
 * {@code faithfulness}, {@code answerRelevancy}) are computed asynchronously
 * by {@code RagScoringService} after ingestion and may be {@code null} until scoring
 * completes. Deterministic scores (precision, recall from similarity_scores) are
 * populated synchronously at ingestion time.
 */
public record RagQuery(
    @NonNull String queryId,
    @NonNull String spanId,
    @NonNull String runId,
    @NonNull String query,
    @Nullable String retrievedChunks,
    @Nullable String similarityScores,
    long latencyMs,
    @NonNull Map<String, Object> metadata,
    @Nullable Double contextPrecision,
    @Nullable Double contextRecall,
    @Nullable Double faithfulness,
    @Nullable Double answerRelevancy,
    int chunkCount,
    @Nullable String collection,
    int topK
) {

    public RagQuery {
        Objects.requireNonNull(queryId, "queryId");
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(query, "query");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /** Convenience factory for basic ingestion without scores. */
    public static RagQuery ofIngestion(
            @NonNull String queryId,
            @NonNull String spanId,
            @NonNull String runId,
            @NonNull String query,
            @Nullable String retrievedChunks,
            @Nullable String similarityScores,
            long latencyMs,
            @NonNull Map<String, Object> metadata,
            int chunkCount,
            @Nullable String collection,
            int topK) {
        double prec = computePrecision(similarityScores);
        double rec  = computeRecall(similarityScores);
        return new RagQuery(
            queryId, spanId, runId, query, retrievedChunks, similarityScores,
            latencyMs, metadata,
            prec > 0 ? prec : null,
            rec  > 0 ? rec  : null,
            null, null,
            chunkCount, collection, topK
        );
    }

    /**
     * Parses {@code "[0.95, 0.87, 0.72]"} and returns the average.
     * Returns 0.0 when the string is absent or unparseable.
     */
    public static double computePrecision(@Nullable String similarityScores) {
        if (similarityScores == null || similarityScores.isBlank()) return 0.0;
        try {
            String trimmed = similarityScores.trim().replaceAll("[\\[\\]]", "");
            String[] parts = trimmed.split(",");
            double sum = 0;
            int count = 0;
            for (String part : parts) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    sum += Double.parseDouble(p);
                    count++;
                }
            }
            return count > 0 ? sum / count : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Fraction of retrieved chunks with similarity score {@code >= 0.70}.
     */
    public static double computeRecall(@Nullable String similarityScores) {
        if (similarityScores == null || similarityScores.isBlank()) return 0.0;
        try {
            String trimmed = similarityScores.trim().replaceAll("[\\[\\]]", "");
            String[] parts = trimmed.split(",");
            int aboveThreshold = 0;
            int count = 0;
            for (String part : parts) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    double score = Double.parseDouble(p);
                    if (score >= 0.70) aboveThreshold++;
                    count++;
                }
            }
            return count > 0 ? (double) aboveThreshold / count : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
