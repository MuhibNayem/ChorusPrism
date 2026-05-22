package com.chorus.observe.budget;

import org.jspecify.annotations.NonNull;

/**
 * Thrown when an agent invocation is blocked because the budget has been exceeded.
 */
public final class BudgetExceededException extends RuntimeException {
    public BudgetExceededException(@NonNull String message) {
        super(message);
    }
}
