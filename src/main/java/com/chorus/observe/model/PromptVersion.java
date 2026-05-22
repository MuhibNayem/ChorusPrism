package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned prompt for the prompt registry.
 */
public record PromptVersion(
    @NonNull String versionId,
    @NonNull String name,
    @NonNull String content,
    @Nullable String model,
    @Nullable Double temperature,
    @Nullable Integer maxTokens,
    @NonNull Map<String, Object> metadata,
    @Nullable String createdBy,
    @NonNull Instant createdAt
) {
    public PromptVersion {
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
