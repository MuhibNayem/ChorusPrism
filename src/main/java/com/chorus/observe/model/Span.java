package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A span represents a single operation within a run.
 */
public record Span(
    @NonNull String spanId,
    @NonNull String runId,
    @Nullable String parentSpanId,
    @NonNull String spanName,
    @NonNull Kind kind,
    @NonNull Instant startTime,
    @Nullable Instant endTime,
    @NonNull Map<String, Object> attributes,
    @NonNull List<SpanEvent> events,
    @NonNull Status status,
    @Nullable String spanType,
    @Nullable Instant firstTokenAt
) {

    public Span {
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(spanName, "spanName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(startTime, "startTime");
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        events = events != null ? List.copyOf(events) : List.of();
        status = Objects.requireNonNull(status, "status");
    }

    public enum Kind {
        INTERNAL,
        SERVER,
        CLIENT,
        PRODUCER,
        CONSUMER
    }

    public enum Status {
        UNSET,
        OK,
        ERROR
    }

    /**
     * An event that occurred during a span.
     */
    public record SpanEvent(
        @NonNull String name,
        @NonNull Instant timestamp,
        @NonNull Map<String, Object> attributes
    ) {
        public SpanEvent {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(timestamp, "timestamp");
            attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        }
    }
}
