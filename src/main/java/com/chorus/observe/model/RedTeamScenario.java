package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Adversarial test scenario for red teaming.
 */
public record RedTeamScenario(
    @NonNull String scenarioId,
    @NonNull String name,
    @NonNull String category,
    @NonNull String attackPrompt,
    @Nullable String expectedBehavior,
    @NonNull Severity severity,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt
) {
    public RedTeamScenario {
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(attackPrompt, "attackPrompt");
        Objects.requireNonNull(severity, "severity");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
