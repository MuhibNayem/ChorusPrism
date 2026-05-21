package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Human or automated feedback on a run or span.
 */
public record Feedback(
    @NonNull String feedbackId,
    @NonNull String runId,
    @Nullable String spanId,
    @Nullable Double score,
    @Nullable String label,
    @Nullable String comment,
    @NonNull String source,
    @NonNull Instant createdAt
) {

    public Feedback {
        Objects.requireNonNull(feedbackId, "feedbackId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
