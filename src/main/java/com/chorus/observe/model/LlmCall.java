package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * A record of an LLM invocation within a span.
 */
public record LlmCall(
    @NonNull String callId,
    @NonNull String spanId,
    @NonNull String runId,
    @NonNull String provider,
    @NonNull String model,
    int inputTokens,
    int outputTokens,
    @NonNull BigDecimal costUsd,
    long latencyMs,
    @Nullable String prompt,
    @Nullable String completion,
    @NonNull List<String> finishReasons
) {

    public LlmCall {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        costUsd = costUsd != null ? costUsd : BigDecimal.ZERO;
        finishReasons = finishReasons != null ? List.copyOf(finishReasons) : List.of();
    }
}
