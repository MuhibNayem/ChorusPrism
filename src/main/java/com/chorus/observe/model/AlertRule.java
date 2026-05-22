package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Alert rule for production monitoring.
 */
public record AlertRule(
    @NonNull String ruleId,
    @NonNull String name,
    @NonNull String conditionExpr,
    double threshold,
    @NonNull Severity severity,
    @Nullable String webhookUrl,
    @Nullable String email,
    boolean enabled,
    int cooldownSeconds,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public AlertRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(conditionExpr, "conditionExpr");
        Objects.requireNonNull(severity, "severity");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
