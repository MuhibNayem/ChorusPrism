package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Budget enforcement tracking for an agent.
 */
public record BudgetEnforcement(
    @NonNull String enforcementId,
    @NonNull String agentId,
    @NonNull String budgetType,
    @NonNull BigDecimal limitValue,
    @NonNull BigDecimal currentValue,
    @NonNull String currency,
    @NonNull Status status,
    @Nullable Instant triggeredAt,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public BudgetEnforcement {
        Objects.requireNonNull(enforcementId, "enforcementId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(budgetType, "budgetType");
        Objects.requireNonNull(limitValue, "limitValue");
        Objects.requireNonNull(currentValue, "currentValue");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public enum Status {
        ACTIVE,
        WARNING,
        EXCEEDED,
        PAUSED
    }
}
