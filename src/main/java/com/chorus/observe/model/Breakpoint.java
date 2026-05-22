package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Live breakpoint injected into an actively executing run.
 */
public record Breakpoint(
    @NonNull String breakpointId,
    @NonNull String runId,
    @Nullable String beforeNode,
    @Nullable String beforeTool,
    @NonNull Status status,
    @NonNull Instant createdAt
) {
    public Breakpoint {
        Objects.requireNonNull(breakpointId, "breakpointId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(status, "status");
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public enum Status {
        ACTIVE,
        TRIGGERED,
        RESOLVED,
        EXPIRED
    }
}
