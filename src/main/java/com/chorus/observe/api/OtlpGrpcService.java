package com.chorus.observe.api;

import com.chorus.observe.model.Span;
import com.chorus.observe.service.OtlpIngestionService;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Status;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * gRPC service for OTLP trace ingestion (port 4317 style).
 */
public class OtlpGrpcService extends TraceServiceGrpc.TraceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpGrpcService.class);

    private final OtlpIngestionService ingestionService;

    public OtlpGrpcService(@NonNull OtlpIngestionService ingestionService) {
        this.ingestionService = Objects.requireNonNull(ingestionService);
    }

    @Override
    public void export(ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
        try {
            List<OtlpIngestionService.OtlpSpan> spans = new ArrayList<>();

            for (ResourceSpans rs : request.getResourceSpansList()) {
                for (ScopeSpans ss : rs.getScopeSpansList()) {
                    for (io.opentelemetry.proto.trace.v1.Span span : ss.getSpansList()) {
                        spans.add(convertSpan(span));
                    }
                }
            }

            ingestionService.ingestSpans(spans);
            responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Failed to process OTLP gRPC export", e);
            responseObserver.onError(e);
        }
    }

    private OtlpIngestionService.OtlpSpan convertSpan(io.opentelemetry.proto.trace.v1.Span span) {
        Map<String, Object> attributes = new HashMap<>();
        for (io.opentelemetry.proto.common.v1.KeyValue kv : span.getAttributesList()) {
            attributes.put(kv.getKey(), extractAttributeValue(kv.getValue()));
        }

        List<Span.SpanEvent> events = new ArrayList<>();
        for (io.opentelemetry.proto.trace.v1.Span.Event ev : span.getEventsList()) {
            events.add(new Span.SpanEvent(
                ev.getName(),
                Instant.ofEpochMilli(ev.getTimeUnixNano() / 1_000_000),
                Map.of()
            ));
        }

        int statusCode = 0;
        if (span.hasStatus()) {
            int codeValue = span.getStatus().getCodeValue();
            statusCode = codeValue == 1 ? 1 : (codeValue == 2 ? 2 : 0);
        }

        String parentSpanId = span.getParentSpanId().isEmpty() ? "" : bytesToHex(span.getParentSpanId().toByteArray());

        return new OtlpIngestionService.OtlpSpan(
            bytesToHex(span.getTraceId().toByteArray()),
            bytesToHex(span.getSpanId().toByteArray()),
            span.getName(),
            Instant.ofEpochMilli(span.getStartTimeUnixNano() / 1_000_000),
            Instant.ofEpochMilli(span.getEndTimeUnixNano() / 1_000_000),
            span.getKindValue(),
            statusCode,
            attributes,
            events,
            parentSpanId
        );
    }

    private Object extractAttributeValue(io.opentelemetry.proto.common.v1.AnyValue value) {
        return switch (value.getValueCase()) {
            case STRING_VALUE -> value.getStringValue();
            case INT_VALUE -> value.getIntValue();
            case DOUBLE_VALUE -> value.getDoubleValue();
            case BOOL_VALUE -> value.getBoolValue();
            default -> value.toString();
        };
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
