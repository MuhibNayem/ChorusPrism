package com.chorus.observe.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Jackson-deserializable DTO for OTLP HTTP JSON trace export requests.
 * Eliminates all unchecked casts by using structured records.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpTraceRequest(
    @JsonProperty("resourceSpans") @NonNull List<ResourceSpans> resourceSpans
) {
    public OtlpTraceRequest {
        resourceSpans = resourceSpans != null ? List.copyOf(resourceSpans) : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceSpans(
        @JsonProperty("scopeSpans") @NonNull List<ScopeSpans> scopeSpans
    ) {
        public ResourceSpans {
            scopeSpans = scopeSpans != null ? List.copyOf(scopeSpans) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScopeSpans(
        @JsonProperty("spans") @NonNull List<Span> spans
    ) {
        public ScopeSpans {
            spans = spans != null ? List.copyOf(spans) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Span(
        @JsonProperty("traceId") @NonNull String traceId,
        @JsonProperty("spanId") @NonNull String spanId,
        @JsonProperty("parentSpanId") @Nullable String parentSpanId,
        @JsonProperty("name") @NonNull String name,
        @JsonProperty("kind") int kind,
        @JsonProperty("startTimeUnixNano") @Nullable String startTimeUnixNano,
        @JsonProperty("endTimeUnixNano") @Nullable String endTimeUnixNano,
        @JsonProperty("attributes") @NonNull List<Attribute> attributes,
        @JsonProperty("events") @NonNull List<Event> events,
        @JsonProperty("status") @Nullable Status status
    ) {
        public Span {
            traceId = Objects.requireNonNullElse(traceId, "");
            spanId = Objects.requireNonNullElse(spanId, "");
            name = Objects.requireNonNullElse(name, "unknown");
            attributes = attributes != null ? List.copyOf(attributes) : List.of();
            events = events != null ? List.copyOf(events) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attribute(
        @JsonProperty("key") @NonNull String key,
        @JsonProperty("value") @NonNull AttributeValue value
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttributeValue(
        @JsonProperty("stringValue") @Nullable String stringValue,
        @JsonProperty("intValue") @Nullable Long intValue,
        @JsonProperty("doubleValue") @Nullable Double doubleValue,
        @JsonProperty("boolValue") @Nullable Boolean boolValue
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
        @JsonProperty("name") @NonNull String name,
        @JsonProperty("timeUnixNano") @Nullable String timeUnixNano
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(
        @JsonProperty("code") int code
    ) {}
}
