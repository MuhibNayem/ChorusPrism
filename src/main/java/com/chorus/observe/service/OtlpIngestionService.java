package com.chorus.observe.service;

import com.chorus.observe.model.*;
import com.chorus.observe.model.RagAttributes;
import com.chorus.observe.persistence.AgentRepository;
import com.chorus.observe.persistence.RagQueryRepository;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.config.ChorusObserveProperties;
import com.chorus.observe.persistence.IngestionQueueRepository;
import com.chorus.observe.store.SpanStore;
import com.fasterxml.jackson.core.type.TypeReference;
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

import com.chorus.observe.event.RunCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
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
    private final AgentRepository agentRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<String, RunAccumulator> runAccumulators = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictionScheduler;

    private final ChorusObserveProperties properties;
    private final IngestionQueueRepository queueRepository;
    private RagQueryRepository ragQueryRepository;
    private RagScoringService ragScoringService;
    private final ScheduledExecutorService queueScheduler;

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper) {
        this(runRepository, spanStore, mapper, null, null, null, null);
    }

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            SpanStreamService streamService) {
        this(runRepository, spanStore, mapper, streamService, null, null, null);
    }

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            SpanStreamService streamService,
            @Nullable MetricsService metricsService) {
        this(runRepository, spanStore, mapper, streamService, metricsService, null, null);
    }

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            SpanStreamService streamService,
            @Nullable MetricsService metricsService,
            @Nullable AgentRepository agentRepository) {
        this(runRepository, spanStore, mapper, streamService, metricsService, agentRepository, null);
    }

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            SpanStreamService streamService,
            @Nullable MetricsService metricsService,
            @Nullable AgentRepository agentRepository,
            @Nullable ApplicationEventPublisher eventPublisher) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.spanStore = Objects.requireNonNull(spanStore);
        this.mapper = Objects.requireNonNull(mapper);
        this.streamService = streamService;
        this.metricsService = metricsService;
        this.agentRepository = agentRepository;
        this.eventPublisher = eventPublisher;
        this.properties = null;
        this.queueRepository = null;
        this.queueScheduler = null;
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chorus-observe-accumulator-eviction");
            t.setDaemon(true);
            return t;
        });
        this.evictionScheduler.scheduleAtFixedRate(
            this::evictStaleAccumulators, EVICTION_INTERVAL_MINUTES, EVICTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public void setRagQueryRepository(@Nullable RagQueryRepository ragQueryRepository) {
        this.ragQueryRepository = ragQueryRepository;
    }

    public void setRagScoringService(@Nullable RagScoringService ragScoringService) {
        this.ragScoringService = ragScoringService;
    }

    public OtlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            @NonNull ChorusObserveProperties properties,
            @NonNull IngestionQueueRepository queueRepository,
            SpanStreamService streamService,
            @Nullable MetricsService metricsService,
            @Nullable AgentRepository agentRepository,
            @Nullable ApplicationEventPublisher eventPublisher) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.spanStore = Objects.requireNonNull(spanStore);
        this.mapper = Objects.requireNonNull(mapper);
        this.properties = Objects.requireNonNull(properties);
        this.queueRepository = Objects.requireNonNull(queueRepository);
        this.streamService = streamService;
        this.metricsService = metricsService;
        this.agentRepository = agentRepository;
        this.eventPublisher = eventPublisher;
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chorus-observe-accumulator-eviction");
            t.setDaemon(true);
            return t;
        });
        this.evictionScheduler.scheduleAtFixedRate(
            this::evictStaleAccumulators, EVICTION_INTERVAL_MINUTES, EVICTION_INTERVAL_MINUTES, TimeUnit.MINUTES);

        if (properties.getIngestionQueue().isEnabled()) {
            this.queueScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "chorus-observe-ingestion-queue-poller");
                t.setDaemon(true);
                return t;
            });
            long pollInterval = properties.getIngestionQueue().getPollIntervalMillis();
            this.queueScheduler.scheduleWithFixedDelay(
                this::pollAndProcessQueue, pollInterval, pollInterval, TimeUnit.MILLISECONDS);
            LOG.info("Asynchronous ingestion queue poller scheduled with interval {}ms", pollInterval);
        } else {
            this.queueScheduler = null;
        }
    }

    /**
     * Ingest a batch of OTLP spans. All spans in the batch are persisted in bulk.
     */
    @Timed(value = "ingestion.spans.batch", description = "Time spent ingesting a batch of spans")
    @Counted(value = "ingestion.spans.batches", description = "Total number of span batch ingestions")
    public void ingestSpans(@NonNull List<OtlpSpan> spans) {
        if (spans.isEmpty()) return;

        if (properties != null && properties.getIngestionQueue().isEnabled() && queueRepository != null) {
            try {
                queueRepository.enqueue(spans);
                LOG.debug("Enqueued {} spans to database outbox", spans.size());
            } catch (Exception e) {
                LOG.warn("Failed to enqueue spans to database outbox; falling back to direct ingestion: {}", e.getMessage());
                ingestSpansInternal(spans);
            }
        } else {
            ingestSpansInternal(spans);
        }
    }

    /**
     * Perform the actual extraction, run accumulation, and bulk persistence of spans.
     */
    public void ingestSpansInternal(@NonNull List<OtlpSpan> spans) {
        if (spans.isEmpty()) return;

        List<Span> allSpans = new ArrayList<>(spans.size());
        List<LlmCall> allLlmCalls = new ArrayList<>();
        List<ToolCall> allToolCalls = new ArrayList<>();
        List<RagQuery> allRagQueries = new ArrayList<>();
        Set<String> affectedRunIds = new HashSet<>();

        for (OtlpSpan otlp : spans) {
            try {
                String runId = otlp.attributes().getOrDefault("chorus.run_id", otlp.traceId()).toString();
                affectedRunIds.add(runId);

                Span.Kind kind = mapSpanKind(otlp.kind());
                Span.Status status = mapStatus(otlp.statusCode());
                String spanType = classifySpanType(otlp);
                Instant firstTokenAt = extractFirstTokenAt(otlp);

                Span span = new Span(
                    otlp.spanId(), runId, otlp.parentSpanId(), otlp.name(),
                    kind, otlp.startTime(), otlp.endTime(),
                    new HashMap<>(otlp.attributes()), otlp.events(), status,
                    spanType, firstTokenAt
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

                if ("rag".equals(spanType) && ragQueryRepository != null) {
                    RagQuery ragQuery = extractRagQuery(otlp, runId);
                    if (ragQuery != null) allRagQueries.add(ragQuery);
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
        if (!allRagQueries.isEmpty() && ragQueryRepository != null) {
            for (RagQuery rq : allRagQueries) {
                try {
                    ragQueryRepository.save(rq);
                    if (ragScoringService != null) {
                        ragScoringService.scoreAsync(rq);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to persist RAG query {}: {}", rq.queryId(), e.getMessage());
                }
            }
        }

        // Flush affected runs once per batch
        for (String runId : affectedRunIds) {
            flushRun(runId);
        }

        if (metricsService != null) {
            metricsService.incrementIngestionSpansTotal(spans.size());
        }
    }

    /**
     * Poll and process queued outbox spans in background virtual-thread scheduled executor.
     */
    private void pollAndProcessQueue() {
        if (queueRepository == null) return;
        try {
            int batchSize = properties.getIngestionQueue().getBatchSize();
            List<IngestionQueueRepository.QueueRecord> records = queueRepository.fetchBatch(batchSize);
            if (records.isEmpty()) return;

            List<OtlpSpan> spans = new ArrayList<>(records.size());
            List<String> queueIds = new ArrayList<>(records.size());
            for (var record : records) {
                spans.add(record.span());
                queueIds.add(record.queueId());
            }

            // Ingest these spans in bulk using the internal logic
            ingestSpansInternal(spans);

            // Dequeue completed records from the outbox table
            queueRepository.dequeue(queueIds);
            LOG.debug("Successfully processed and dequeued {} spans", records.size());
        } catch (Exception e) {
            LOG.error("Failed to poll or process ingestion queue records: {}", e.getMessage(), e);
        }
    }

    private void accumulateRun(@NonNull String runId, @NonNull OtlpSpan otlp) {
        RunAccumulator acc = runAccumulators.computeIfAbsent(runId, k -> new RunAccumulator());
        synchronized (acc) {
            acc.lastAccessed = Instant.now();

            Object frameworkAttr = otlp.attributes().get("chorus.framework");
            if (frameworkAttr != null) {
                acc.framework = frameworkAttr.toString();
            }
            Object agentIdAttr = otlp.attributes().get("gen_ai.agent.id");
            if (agentIdAttr != null) {
                acc.agentId = agentIdAttr.toString();
            }
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

            if (agentRepository != null && !acc.agentUpserted) {
                acc.agentUpserted = true;
                agentRepository.upsertFromRun(acc.agentId, acc.framework);
            }
        }
    }

    private void flushRun(@NonNull String runId) {
        RunAccumulator acc = runAccumulators.get(runId);
        if (acc == null || acc.startTime == null) return;

        synchronized (acc) {
            long latencyMs = 0;
            if (acc.endTime != null) {
                latencyMs = Duration.between(acc.startTime, acc.endTime).toMillis();
            }

            Map<String, String> tags = new HashMap<>();
            Map<String, Object> metadata = new HashMap<>();

            String tenantId = TenantContext.getTenantIdOrNull();
            if (tenantId == null) {
                tenantId = "default";
            }

            Run run = new Run(
                runId,
                tenantId,
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

            if (eventPublisher != null && acc.endTime != null && !acc.completionEventPublished
                && (acc.status == Run.Status.SUCCESS || acc.status == Run.Status.ERROR)) {
                acc.completionEventPublished = true;
                eventPublisher.publishEvent(new RunCompletedEvent(this, runId, tenantId, acc.status));
            }
        }
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

        List<LlmCall.LlmMessage> messages = parseLlmMessages(attrs.get("gen_ai.messages"));

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
            finishReasons,
            messages
        );
    }

    private @Nullable List<LlmCall.LlmMessage> parseLlmMessages(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            try {
                List<Map<String, Object>> list = mapper.readValue(s, new TypeReference<>() {});
                return list.stream()
                    .map(m -> new LlmCall.LlmMessage(
                        Objects.toString(m.get("role"), ""),
                        Objects.toString(m.get("text"), "")))
                    .filter(m -> !m.role().isEmpty())
                    .toList();
            } catch (Exception e) {
                LOG.warn("Failed to parse gen_ai.messages JSON string: {}", e.getMessage());
                return null;
            }
        }
        if (raw instanceof List<?> list) {
            List<LlmCall.LlmMessage> messages = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String role = Objects.toString(map.get("role"), null);
                    String text = Objects.toString(map.get("text"), null);
                    if (role != null && text != null) {
                        messages.add(new LlmCall.LlmMessage(role, text));
                    }
                }
            }
            return messages.isEmpty() ? null : messages;
        }
        return null;
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

    private @Nullable RagQuery extractRagQuery(@NonNull OtlpSpan otlp, @NonNull String runId) {
        Map<String, Object> attrs = otlp.attributes();
        long latencyMs = 0;
        if (otlp.endTime() != null) {
            latencyMs = Duration.between(otlp.startTime(), otlp.endTime()).toMillis();
        }

        // Support both Chorus-native (rag.*) and OTel semantic convention aliases
        String queryText = Objects.toString(
            attrs.getOrDefault(RagAttributes.QUERY_TEXT,
                attrs.getOrDefault(RagAttributes.GEN_AI_RETRIEVAL_QUERY,
                    attrs.getOrDefault("db.query.text", null))), null);
        if (queryText == null) return null;

        String retrievedChunks = Objects.toString(attrs.get(RagAttributes.RETRIEVED_CHUNKS), null);
        String similarityScores = Objects.toString(attrs.get(RagAttributes.SIMILARITY_SCORES), null);
        String collection = Objects.toString(
            attrs.getOrDefault(RagAttributes.COLLECTION,
                attrs.getOrDefault(RagAttributes.DB_VECTOR_COLLECTION_NAME, null)), null);

        int topK = 5;
        Object topKAttr = attrs.getOrDefault(RagAttributes.TOP_K, attrs.get(RagAttributes.DB_VECTOR_QUERY_TOP_K));
        if (topKAttr instanceof Number n) topK = n.intValue();

        int chunkCount = 0;
        Object chunkAttr = attrs.getOrDefault(RagAttributes.CHUNK_COUNT, attrs.get(RagAttributes.DB_VECTOR_RESULT_COUNT));
        if (chunkAttr instanceof Number n) {
            chunkCount = n.intValue();
        } else if (similarityScores != null) {
            chunkCount = (int) similarityScores.chars().filter(c -> c == ',').count() + 1;
        }

        Map<String, Object> metadata = new HashMap<>();
        Object cacheHit = attrs.get(RagAttributes.CACHE_HIT);
        if (cacheHit != null) metadata.put("cache_hit", cacheHit.toString());
        Object agentId = attrs.get(RagAttributes.GEN_AI_AGENT_ID);
        if (agentId != null) metadata.put("agent", agentId.toString());

        return RagQuery.ofIngestion(
            otlp.spanId() + ":rag",
            otlp.spanId(),
            runId,
            queryText,
            retrievedChunks,
            similarityScores,
            latencyMs,
            metadata,
            chunkCount,
            collection,
            topK
        );
    }

    private @Nullable String classifySpanType(@NonNull OtlpSpan otlp) {
        String name = otlp.name();
        Map<String, Object> attrs = otlp.attributes();
        if (name.startsWith("llm.") || attrs.containsKey("gen_ai.system")) {
            return "llm";
        }
        if (name.startsWith("tool.") || attrs.containsKey("chorus.tool.name")) {
            return "tool";
        }
        if (name.startsWith("rag.") || attrs.containsKey(RagAttributes.COLLECTION)
                || attrs.containsKey(RagAttributes.DB_VECTOR_COLLECTION_NAME)
                || attrs.containsKey(RagAttributes.GEN_AI_RETRIEVAL_QUERY)
                || name.startsWith("db.vector.") || name.startsWith("retrieval.")) {
            return "rag";
        }
        if (name.startsWith("guardrail.") || attrs.containsKey("guard.policy")) {
            return "guardrail";
        }
        return "default";
    }

    private @Nullable Instant extractFirstTokenAt(@NonNull OtlpSpan otlp) {
        Object firstTokenMs = otlp.attributes().get("gen_ai.response.first_token_ms");
        if (firstTokenMs instanceof Number n) {
            return otlp.startTime().plusMillis(n.longValue());
        }
        return null;
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
        boolean agentUpserted = false;
        boolean completionEventPublished = false;
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
        if (queueScheduler != null) {
            queueScheduler.shutdown();
        }
        try {
            if (!evictionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                evictionScheduler.shutdownNow();
            }
            if (queueScheduler != null && !queueScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                queueScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            evictionScheduler.shutdownNow();
            if (queueScheduler != null) {
                queueScheduler.shutdownNow();
            }
            Thread.currentThread().interrupt();
        }
    }
}
