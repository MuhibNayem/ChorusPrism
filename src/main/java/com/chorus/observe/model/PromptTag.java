package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Tag attached to a prompt version (e.g., "production", "staging").
 */
public record PromptTag(
    @NonNull String versionId,
    @NonNull String tagName,
    @NonNull Instant createdAt
) {
    public PromptTag {
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(tagName, "tagName");
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
