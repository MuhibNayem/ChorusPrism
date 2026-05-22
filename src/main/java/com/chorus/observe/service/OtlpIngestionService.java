package com.chorus.observe.service;

import com.chorus.observe.model.*;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.store.SpanStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;

/**
 * Transforms OTLP spans into Chorus Observe domain models and persists them.
 * Thread-safe. Designed for high-throughput ingestion.
 * <p>
 * Batching strategy:
 * <ul>
 *   <li>Within a single {@link #ingestSpans} call, all spans are accumulated and flushed in bulk.</li>
 *   <li>{@link SpanStore} receives batch inserts (not N individual round-trips).</li>
 *   <li>Run accumulators are flushed once per batch, not once per span.</li>
 * </ul>
 * <p>
 * Memory safety:
 * <ul>
 *   <li>Run accumulators auto-expire after {@value #ACCUMULATOR_TTL_MINUTES} minutes of inactivity.</li>
 *   <li>A background task evicts stale entries every 5 minutes.</li>
 * </ul>
 */
public class OtlpIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpIngestionService.class);
    private static final int ACCUMULATOR_TTL_MINUTES = 60;
    private static final int EVICTION_INTERVAL_MINUTES = 5;

    private final RunRepository runRepository;
    private final SpanStore spanStore;
    private final ObjectMapper mapper;
    private final SpanStreamService streamService;
    private final MetricsService metricsService;

    private final ConcurrentHashMap<String, RunAccumulator> runAccumulators = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictionScheduler;

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper) {
        this(runRepository, spanStore, mapper, null, null);
    }

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            SpanStreamService streamService) {
        this(runRepository, spanStore, mapper, streamService, null);
    }

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            SpanStreamService streamService,
            @Nullable MetricsService metricsService) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.spanStore = Objects.requireNonNull(spanStore);
        this.mapper = Objects.requireNonNull(mapper);
        this.streamService = streamService;
        this.metricsService = metricsService;
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chorus-observe-accumulator-eviction");
            t.setDaemon(true);
            return t;
        });
        this.evictionScheduler.scheduleAtFixedRate(
            this::evictStaleAccumulators, EVICTION_INTERVAL_MINUTES, EVICTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Ingest a batch of OTLP spans. All spans in the batch are persisted in bulk.
     */
    @Timed(value = "ingestion.spans.batch", description = "Time spent ingesting a batch of spans")
    @Counted(value = "ingestion.spans.batches", description = "Total number of span batch ingestions")
    public void ingestSpans(@NonNull List<OtlpSpan> spans) {
        if (spans.isEmpty()) return;

        List<Span> allSpans = new ArrayList<>(spans.size());
        List<LlmCall> allLlmCalls = new ArrayList<>();
        List<ToolCall> allToolCalls = new ArrayList<>();
        Set<String> affectedRunIds = new HashSet<>();

        for (OtlpSpan otlp : spans) {
            try {
                String runId = otlp.attributes().getOrDefault("chorus.run_id", otlp.traceId()).toString();
                affectedRunIds.add(runId);

                Span.Kind kind = mapSpanKind(otlp.kind());
                Span.Status status = mapStatus(otlp.statusCode());

                Span span = new Span(
                    otlp.spanId(), runId, otlp.parentSpanId(), otlp.name(),
                    kind, otlp.startTime(), otlp.endTime(),
                    new HashMap<>(otlp.attributes()), otlp.events(), status
                );
                allSpans.add(span);

                if (otlp.attributes().containsKey("gen_ai.system") || otlp.attributes().containsKey("gen_ai.request.model")) {
                    LlmCall llmCall = extractLlmCall(otlp, runId);
                    if (llmCall != null) allLlmCalls.add(llmCall);
                }

                if (otlp.attributes().containsKey("gen_ai.tool.name") || otlp.attributes().containsKey("chorus.tool.name")) {
                    ToolCall toolCall = extractToolCall(otlp, runId);
                    if (toolCall != null) allToolCalls.add(toolCall);
                }

                if (streamService != null) {
                    streamService.publish(runId, span);
                }

                accumulateRun(runId, otlp);
            } catch (Exception e) {
                LOG.warn("Failed to ingest span {}: {}", otlp.spanId(), e.getMessage());
            }
        }

        // Bulk persist
        if (!allSpans.isEmpty()) {
            spanStore.saveSpans(allSpans);
        }
        if (!allLlmCalls.isEmpty()) {
            spanStore.saveLlmCalls(allLlmCalls);
        }
        if (!allToolCalls.isEmpty()) {
            spanStore.saveToolCalls(allToolCalls);
        }

        // Flush affected runs once per batch
        for (String runId : affectedRunIds) {
            flushRun(runId);
        }

        if (metricsService != null) {
            metricsService.incrementIngestionSpansTotal(spans.size());
        }
    }

    private void accumulateRun(@NonNull String runId, @NonNull OtlpSpan otlp) {
        RunAccumulator acc = runAccumulators.computeIfAbsent(runId, k -> new RunAccumulator());
        acc.lastAccessed = Instant.now();

        acc.framework = otlp.attributes().getOrDefault("chorus.framework", acc.framework).toString();
        acc.agentId = otlp.attributes().getOrDefault("gen_ai.agent.id", acc.agentId).toString();
        Object model = otlp.attributes().get("gen_ai.request.model");
        if (model != null) {
            acc.model = model.toString();
        }

        if (acc.startTime == null || otlp.startTime().isBefore(acc.startTime)) {
            acc.startTime = otlp.startTime();
        }
        if (acc.endTime == null || (otlp.endTime() != null && otlp.endTime().isAfter(acc.endTime))) {
            acc.endTime = otlp.endTime();
        }

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

        if (otlp.statusCode() == 2) { // ERROR
            acc.status = Run.Status.ERROR;
        } else if (acc.status == Run.Status.RUNNING && otlp.endTime() != null) {
            acc.status = Run.Status.SUCCESS;
        }
    }

    private void flushRun(@NonNull String runId) {
        RunAccumulator acc = runAccumulators.get(runId);
        if (acc == null || acc.startTime == null) return;

        long latencyMs = 0;
        if (acc.endTime != null) {
            latencyMs = Duration.between(acc.startTime, acc.endTime).toMillis();
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

    private void evictStaleAccumulators() {
        try {
            Instant cutoff = Instant.now().minusSeconds(ACCUMULATOR_TTL_MINUTES * 60L);
            int before = runAccumulators.size();
            runAccumulators.entrySet().removeIf(e -> e.getValue().lastAccessed.isBefore(cutoff));
            int after = runAccumulators.size();
            if (before != after) {
                LOG.debug("Evicted {} stale run accumulators ({} remaining)", before - after, after);
            }
        } catch (Exception e) {
            LOG.warn("Accumulator eviction task failed", e);
        }
    }

    private @Nullable LlmCall extractLlmCall(@NonNull OtlpSpan otlp, @NonNull String runId) {
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
            latencyMs = Duration.between(otlp.startTime(), otlp.endTime()).toMillis();
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

    private @Nullable ToolCall extractToolCall(@NonNull OtlpSpan otlp, @NonNull String runId) {
        Map<String, Object> attrs = otlp.attributes();
        long latencyMs = 0;
        if (otlp.endTime() != null) {
            latencyMs = Duration.between(otlp.startTime(), otlp.endTime()).toMillis();
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
        Instant lastAccessed = Instant.now();
    }

    /**
     * Normalized OTLP span for internal processing.
     */
    public record OtlpSpan(
        @NonNull String traceId,
        @NonNull String spanId,
        @NonNull String name,
        @NonNull Instant startTime,
        @Nullable Instant endTime,
        int kind,
        int statusCode,
        @NonNull Map<String, Object> attributes,
        @NonNull List<Span.SpanEvent> events,
        @Nullable String parentSpanId
    ) {}

    @PreDestroy
    public void close() {
        evictionScheduler.shutdown();
        try {
            if (!evictionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                evictionScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            evictionScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
