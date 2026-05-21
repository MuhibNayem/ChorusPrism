package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A record of a tool invocation within a span.
 */
public record ToolCall(
    @NonNull String callId,
    @NonNull String spanId,
    @NonNull String runId,
    @NonNull String toolName,
    @Nullable String args,
    @Nullable String result,
    long latencyMs,
    @Nullable String error
) {

    public ToolCall {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(toolName, "toolName");
    }
}
