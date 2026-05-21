package com.chorus.observe.api;

import com.chorus.observe.api.dto.OtlpTraceRequest;
import com.chorus.observe.model.Span;
import com.chorus.observe.service.OtlpIngestionService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP/JSON endpoint for OTLP trace ingestion (port 4318 style).
 * Accepts structured {@link OtlpTraceRequest} DTOs for type-safe deserialization.
 */
@RestController
@RequestMapping("/v1/traces")
public class OtlpHttpController {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpHttpController.class);

    private final OtlpIngestionService ingestionService;

    public OtlpHttpController(@NonNull OtlpIngestionService ingestionService) {
        this.ingestionService = Objects.requireNonNull(ingestionService);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingestTraces(@RequestBody @NonNull OtlpTraceRequest request) {
        try {
            List<OtlpIngestionService.OtlpSpan> spans = convertRequest(request);
            ingestionService.ingestSpans(spans);
            return ResponseEntity.ok(Map.of(
                "partialSuccess", Map.of(),
                "spansReceived", spans.size()
            ));
        } catch (Exception e) {
            LOG.error("Failed to ingest traces", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    private List<OtlpIngestionService.OtlpSpan> convertRequest(OtlpTraceRequest request) {
        List<OtlpIngestionService.OtlpSpan> result = new ArrayList<>();

        for (OtlpTraceRequest.ResourceSpans rs : request.resourceSpans()) {
            for (OtlpTraceRequest.ScopeSpans ss : rs.scopeSpans()) {
                for (OtlpTraceRequest.Span span : ss.spans()) {
                    result.add(convertSpan(span));
                }
            }
        }

        return result;
    }

    private OtlpIngestionService.OtlpSpan convertSpan(OtlpTraceRequest.Span span) {
        Map<String, Object> attributes = new HashMap<>();
        for (OtlpTraceRequest.Attribute attr : span.attributes()) {
            attributes.put(attr.key(), extractAttributeValue(attr.value()));
        }

        List<Span.SpanEvent> events = new ArrayList<>();
        for (OtlpTraceRequest.Event ev : span.events()) {
            events.add(new Span.SpanEvent(
                ev.name(),
                parseTimestamp(ev.timeUnixNano()),
                Map.of()
            ));
        }

        int statusCode = span.status() != null ? span.status().code() : 0;
        String parentSpanId = span.parentSpanId() != null ? span.parentSpanId() : "";

        return new OtlpIngestionService.OtlpSpan(
            span.traceId(),
            span.spanId(),
            span.name(),
            parseTimestamp(span.startTimeUnixNano()),
            parseTimestamp(span.endTimeUnixNano()),
            span.kind(),
            statusCode,
            attributes,
            events,
            parentSpanId
        );
    }

    private Object extractAttributeValue(OtlpTraceRequest.AttributeValue value) {
        if (value.stringValue() != null) return value.stringValue();
        if (value.intValue() != null) return value.intValue();
        if (value.doubleValue() != null) return value.doubleValue();
        if (value.boolValue() != null) return value.boolValue();
        return null;
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            long nanos = Long.parseLong(value);
            return Instant.ofEpochMilli(nanos / 1_000_000);
        } catch (NumberFormatException e) {
            return Instant.parse(value);
        }
    }
}
