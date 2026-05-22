package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A/B test comparing two prompt versions against a dataset.
 */
public record PromptAbTest(
    @NonNull String testId,
    @Nullable String datasetId,
    @NonNull String promptAId,
    @NonNull String promptBId,
    @NonNull Status status,
    @Nullable String winnerId,
    @Nullable Double pValue,
    @NonNull Map<String, Object> summaryMetrics,
    @NonNull Instant createdAt,
    @Nullable Instant finishedAt
) {
    public PromptAbTest {
        Objects.requireNonNull(testId, "testId");
        Objects.requireNonNull(promptAId, "promptAId");
        Objects.requireNonNull(promptBId, "promptBId");
        Objects.requireNonNull(status, "status");
        summaryMetrics = summaryMetrics != null ? Map.copyOf(summaryMetrics) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
