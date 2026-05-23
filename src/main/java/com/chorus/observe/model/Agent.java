package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An agent registered in Chorus Observe.
 */
public record Agent(
    @NonNull String agentId,
    @NonNull String name,
    @Nullable String description,
    @Nullable String framework,
    @Nullable String runtime,
    @Nullable String owner,
    @Nullable String ownerEmail,
    @NonNull List<String> tags,
    @Nullable String version,
    @Nullable Instant deployedAt,
    @Nullable String deployedBy,
    @NonNull Status status,
    @Nullable Double health,
    @Nullable String repo,
    @Nullable String branch
) {

    public Agent {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(name, "name");
        status = Objects.requireNonNull(status, "status");
        tags = tags != null ? List.copyOf(tags) : List.of();
    }

    public enum Status {
        healthy,
        degraded,
        error
    }
}
