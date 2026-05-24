package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An eval case automatically generated from a production trace.
 * Must pass through human review before reaching a dataset.
 */
public record GeneratedEvalCase(
    @NonNull String caseId,
    @NonNull String sourceRunId,
    @Nullable String sourceSpanId,
    @NonNull String input,
    @Nullable String expectedOutput,
    @NonNull Map<String, Object> metadata,
    @NonNull Status status,
    @Nullable String reviewedBy,
    @Nullable Instant reviewedAt,
    @Nullable String reviewNotes,
    @Nullable String datasetId,
    @NonNull Instant createdAt
) {
    public GeneratedEvalCase {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(sourceRunId, "sourceRunId");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(status, "status");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public enum Status {
        GENERATED,
        PENDING_REVIEW,
        APPROVED,
        REJECTED
    }
}
