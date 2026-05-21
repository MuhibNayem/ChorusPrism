package com.chorus.observe.service;

import com.chorus.observe.model.*;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.store.SpanStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transforms OTLP spans into Chorus Observe domain models and persists them.
 * Thread-safe. Designed for high-throughput ingestion.
 * <p>
 * Uses {@link SpanStore} for span/llm/tool persistence (pluggable: PostgreSQL, ClickHouse, or dual-write)
 * and {@link RunRepository} for relational run aggregation.
 */
public class OtlpIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpIngestionService.class);

    private final RunRepository runRepository;
    private final SpanStore spanStore;
    private final ObjectMapper mapper;
    private final SpanStreamService streamService;

    // In-memory buffer for run aggregation before flush
    private final ConcurrentHashMap<String, RunAccumulator> runAccumulators = new ConcurrentHashMap<>();

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper) {
        this(runRepository, spanStore, mapper, null);
    }

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            SpanStreamService streamService) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.spanStore = Objects.requireNonNull(spanStore);
        this.mapper = Objects.requireNonNull(mapper);
        this.streamService = streamService;
    }

    /**
     * Ingest a batch of OTLP spans.
     */
    public void ingestSpans(@NonNull List<OtlpSpan> spans) {
        for (OtlpSpan otlp : spans) {
            try {
                ingestSingleSpan(otlp);
            } catch (Exception e) {
                LOG.warn("Failed to ingest span {}: {}", otlp.spanId(), e.getMessage());
            }
        }
    }

    private void ingestSingleSpan(@NonNull OtlpSpan otlp) {
        String runId = otlp.attributes().getOrDefault("chorus.run_id", otlp.traceId()).toString();

        // Determine span kind and status
        Span.Kind kind = mapSpanKind(otlp.kind());
        Span.Status status = mapStatus(otlp.statusCode());

        // Build and buffer span
        Span span = new Span(
            otlp.spanId(),
            runId,
            otlp.parentSpanId(),
            otlp.name(),
            kind,
            otlp.startTime(),
            otlp.endTime(),
            new HashMap<>(otlp.attributes()),
            otlp.events(),
            status
        );

        // Extract LLM call if gen_ai attributes are present
        LlmCall llmCall = null;
        if (otlp.attributes().containsKey("gen_ai.system") || otlp.attributes().containsKey("gen_ai.request.model")) {
            llmCall = extractLlmCall(otlp, runId);
        }

        // Extract tool call if tool attributes are present
        ToolCall toolCall = null;
        if (otlp.attributes().containsKey("gen_ai.tool.name") || otlp.attributes().containsKey("chorus.tool.name")) {
            toolCall = extractToolCall(otlp, runId);
        }

        // Batch persist via SpanStore
        spanStore.saveSpans(List.of(span));
        if (llmCall != null) {
            spanStore.saveLlmCalls(List.of(llmCall));
        }
        if (toolCall != null) {
            spanStore.saveToolCalls(List.of(toolCall));
        }

        // Stream to subscribers
        if (streamService != null) {
            streamService.publish(runId, span);
        }

        // Accumulate run data
        accumulateRun(runId, otlp);
    }

    private void accumulateRun(@NonNull String runId, @NonNull OtlpSpan otlp) {
        RunAccumulator acc = runAccumulators.computeIfAbsent(runId, k -> new RunAccumulator());

        acc.framework = otlp.attributes().getOrDefault("chorus.framework", "unknown").toString();
        acc.agentId = otlp.attributes().getOrDefault("gen_ai.agent.id", "unknown").toString();
        acc.model = otlp.attributes().getOrDefault("gen_ai.request.model", acc.model).toString();

        if (acc.startTime == null || otlp.startTime().isBefore(acc.startTime)) {
            acc.startTime = otlp.startTime();
        }
        if (acc.endTime == null || (otlp.endTime() != null && otlp.endTime().isAfter(acc.endTime))) {
            acc.endTime = otlp.endTime();
        }

        // Aggregate tokens and cost
        Object inputTokens = otlp.attributes().get("gen_ai.usage.input_tokens");
        if (inputTokens instanceof Number n) {
            acc.totalTokens += n.intValue();
        }
        Object outputTokens = otlp.attributes().get("gen_ai.usage.output_tokens");
        if (outputTokens instanceof Number n) {
            acc.totalTokens += n.intValue();
        }
        Object cost = otlp.attributes().get("chorus.cost_usd");
        if (cost instanceof Number n) {
            acc.totalCost = acc.totalCost.add(BigDecimal.valueOf(n.doubleValue()));
        }

        // Determine status
        if (otlp.statusCode() == 2) { // ERROR
            acc.status = Run.Status.ERROR;
        } else if (acc.status == Run.Status.RUNNING && otlp.endTime() != null) {
            acc.status = Run.Status.SUCCESS;
        }

        // Flush to DB every span for simplicity; in production, batch flush
        flushRun(runId);
    }

    private void flushRun(@NonNull String runId) {
        RunAccumulator acc = runAccumulators.get(runId);
        if (acc == null || acc.startTime == null) return;

        long latencyMs = 0;
        if (acc.endTime != null) {
            latencyMs = java.time.Duration.between(acc.startTime, acc.endTime).toMillis();
        }

        Map<String, String> tags = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();

        Run run = new Run(
            runId,
            acc.framework,
            acc.agentId,
            acc.model.isEmpty() ? null : acc.model,
            acc.startTime,
            acc.endTime,
            acc.status,
            tags,
            metadata,
            acc.totalTokens,
            acc.totalCost.setScale(8, RoundingMode.HALF_UP),
            latencyMs
        );
        runRepository.save(run);
    }

    private LlmCall extractLlmCall(@NonNull OtlpSpan otlp, @NonNull String runId) {
        Map<String, Object> attrs = otlp.attributes();
        int inputTokens = 0;
        int outputTokens = 0;
        long latencyMs = 0;
        BigDecimal cost = BigDecimal.ZERO;

        Object inTok = attrs.get("gen_ai.usage.input_tokens");
        if (inTok instanceof Number n) inputTokens = n.intValue();
        Object outTok = attrs.get("gen_ai.usage.output_tokens");
        if (outTok instanceof Number n) outputTokens = n.intValue();
        Object costVal = attrs.get("chorus.cost_usd");
        if (costVal instanceof Number n) cost = BigDecimal.valueOf(n.doubleValue());
        if (otlp.endTime() != null) {
            latencyMs = java.time.Duration.between(otlp.startTime(), otlp.endTime()).toMillis();
        }

        @SuppressWarnings("unchecked")
        List<String> finishReasons = attrs.get("gen_ai.response.finish_reasons") instanceof List
            ? (List<String>) attrs.get("gen_ai.response.finish_reasons")
            : List.of();

        return new LlmCall(
            otlp.spanId() + ":llm",
            otlp.spanId(),
            runId,
            Objects.toString(attrs.getOrDefault("gen_ai.system", "unknown"), "unknown"),
            Objects.toString(attrs.getOrDefault("gen_ai.request.model", "unknown"), "unknown"),
            inputTokens,
            outputTokens,
            cost.setScale(8, RoundingMode.HALF_UP),
            latencyMs,
            Objects.toString(attrs.get("gen_ai.prompt"), null),
            Objects.toString(attrs.get("gen_ai.completion"), null),
            finishReasons
        );
    }

    private ToolCall extractToolCall(@NonNull OtlpSpan otlp, @NonNull String runId) {
        Map<String, Object> attrs = otlp.attributes();
        long latencyMs = 0;
        if (otlp.endTime() != null) {
            latencyMs = java.time.Duration.between(otlp.startTime(), otlp.endTime()).toMillis();
        }

        String toolName = Objects.toString(
            attrs.getOrDefault("gen_ai.tool.name",
                attrs.getOrDefault("chorus.tool.name", "unknown")), "unknown");

        return new ToolCall(
            otlp.spanId() + ":tool",
            otlp.spanId(),
            runId,
            toolName,
            Objects.toString(attrs.get("chorus.tool.args"), null),
            Objects.toString(attrs.get("chorus.tool.result"), null),
            latencyMs,
            otlp.statusCode() == 2 ? Objects.toString(attrs.get("error.message"), "error") : null
        );
    }

    private Span.Kind mapSpanKind(int otlpKind) {
        return switch (otlpKind) {
            case 1 -> Span.Kind.SERVER;
            case 2 -> Span.Kind.CLIENT;
            case 3 -> Span.Kind.PRODUCER;
            case 4 -> Span.Kind.CONSUMER;
            default -> Span.Kind.INTERNAL;
        };
    }

    private Span.Status mapStatus(int otlpStatus) {
        return switch (otlpStatus) {
            case 1 -> Span.Status.OK;
            case 2 -> Span.Status.ERROR;
            default -> Span.Status.UNSET;
        };
    }

    private static class RunAccumulator {
        String framework = "unknown";
        String agentId = "unknown";
        String model = "";
        Instant startTime;
        Instant endTime;
        Run.Status status = Run.Status.RUNNING;
        int totalTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
    }

    /**
     * Normalized OTLP span for internal processing.
     */
    public record OtlpSpan(
        @NonNull String traceId,
        @NonNull String spanId,
        @NonNull String name,
        @NonNull Instant startTime,
        @NonNull Instant endTime,
        int kind,
        int statusCode,
        @NonNull Map<String, Object> attributes,
        @NonNull List<Span.SpanEvent> events,
        @NonNull String parentSpanId
    ) {}
}
