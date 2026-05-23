package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * An evaluator defines a scoring function for runs.
 */
public record Evaluator(
    @NonNull String evaluatorId,
    @NonNull String name,
    @NonNull String kind,
    @Nullable String description,
    @NonNull Map<String, Object> config
) {

    public Evaluator {
        Objects.requireNonNull(evaluatorId, "evaluatorId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        config = config != null ? Map.copyOf(config) : Map.of();
    }
}
