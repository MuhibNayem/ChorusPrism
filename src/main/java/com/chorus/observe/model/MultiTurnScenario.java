package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A multi-turn conversation scenario for testing conversational agents.
 */
public record MultiTurnScenario(
    @NonNull String scenarioId,
    @NonNull String name,
    @Nullable String description,
    @NonNull List<Turn> turns,
    @NonNull Map<String, Object> metadata,
    @NonNull Instant createdAt
) {
    public MultiTurnScenario {
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(name, "name");
        turns = turns != null ? List.copyOf(turns) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * A single turn in a multi-turn conversation.
     *
     * @param role              "user" or "assistant"
     * @param message           the message content
     * @param expectedKeywords  keywords expected in the agent's response to this turn
     */
    public record Turn(
        @NonNull String role,
        @NonNull String message,
        @NonNull List<String> expectedKeywords
    ) {
        public Turn {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(message, "message");
            expectedKeywords = expectedKeywords != null ? List.copyOf(expectedKeywords) : List.of();
        }
    }
}
