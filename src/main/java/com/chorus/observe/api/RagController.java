package com.chorus.observe.api;

import com.chorus.observe.model.RagQuery;
import com.chorus.observe.persistence.RagQueryRepository;
import com.chorus.observe.service.RagQueryClusteringService;
import com.chorus.observe.service.RagScoringService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * RAG Analytics API — world-class retrieval observability.
 * <p>
 * All endpoints are tenant-scoped via {@code TenantContext} in the repository layer.
 *
 * <pre>
 * GET /api/v1/rag/metrics?window=24h&amp;collection=product_docs
 * GET /api/v1/rag/trend?window=7d&amp;granularity=hour&amp;collection=...
 * GET /api/v1/rag/queries?window=24h&amp;collection=...&amp;page=0&amp;size=20
 * GET /api/v1/rag/queries/{queryId}
 * GET /api/v1/rag/collections?window=7d
 * GET /api/v1/rag/clusters?window=7d&amp;collection=...
 * GET /api/v1/rag/drift?window=30d&amp;collection=...
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final @Nullable RagQueryRepository ragRepo;
    private final @Nullable RagQueryClusteringService clusteringService;

    public RagController() {
        this.ragRepo = null;
        this.clusteringService = null;
    }

    public RagController(
            @NonNull RagQueryRepository ragRepo,
            @Nullable RagQueryClusteringService clusteringService) {
        this.ragRepo = ragRepo;
        this.clusteringService = clusteringService;
    }

    // ── Aggregate metrics KPIs ────────────────────────────────────────────────

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics(
            @RequestParam(defaultValue = "24h") String window,
            @RequestParam(required = false) String collection) {
        if (ragRepo == null) return ResponseEntity.ok(fallbackMetrics());

        Instant[] range = parseWindow(window);
        Map<String, Object> agg = ragRepo.getAggregateMetrics(range[0], range[1], collection);
        List<Map<String, Object>> latencyDist = ragRepo.getLatencyHistogram(range[0], range[1], collection);
        List<Map<String, Object>> topQueries  = ragRepo.getTopQueries(range[0], range[1], collection, 10);
        List<Map<String, Object>> collections = toCollectionList(ragRepo.getCollectionBreakdown(range[0], range[1]));

        long queryCount         = toLong(agg.get("query_count"));
        double avgLatency       = toDouble(agg.get("avg_latency_ms"));
        double p50Latency       = toDouble(agg.get("p50_latency_ms"));
        double p95Latency       = toDouble(agg.get("p95_latency_ms"));
        double p99Latency       = toDouble(agg.get("p99_latency_ms"));
        double avgPrecision     = toDouble(agg.get("avg_context_precision"));
        double avgRecall        = toDouble(agg.get("avg_context_recall"));
        double avgFaithfulness  = toDouble(agg.get("avg_faithfulness"));
        double avgRelevancy     = toDouble(agg.get("avg_answer_relevancy"));
        double hitRate          = toDouble(agg.get("hit_rate"));
        double avgChunkCount    = toDouble(agg.get("avg_chunk_count"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("queryCount",            queryCount);
        response.put("avgContextPrecision",   round(avgPrecision));
        response.put("avgContextRecall",      round(avgRecall));
        response.put("avgFaithfulness",       round(avgFaithfulness));
        response.put("avgAnswerRelevancy",    round(avgRelevancy));
        response.put("hitRate",               round(hitRate));
        response.put("avgLatencyMs",          (long) avgLatency);
        response.put("p50LatencyMs",          (long) p50Latency);
        response.put("p95LatencyMs",          (long) p95Latency);
        response.put("p99LatencyMs",          (long) p99Latency);
        response.put("avgChunkCount",         round(avgChunkCount));
        response.put("latencyDistribution",   latencyDist);
        response.put("topQueries",            topQueries);
        response.put("collections",           collections);

        return ResponseEntity.ok(response);
    }

    private static final Set<String> VALID_GRANULARITIES = Set.of("hour", "day", "week");

    // ── Time-series trend ─────────────────────────────────────────────────────

    @GetMapping("/trend")
    public ResponseEntity<?> getTrend(
            @RequestParam(defaultValue = "7d") String window,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) String collection) {
        if (!VALID_GRANULARITIES.contains(granularity)) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Invalid granularity '" + granularity + "'. Must be one of: hour, day, week"));
        }
        if (ragRepo == null) return ResponseEntity.ok(List.of());

        Instant[] range = parseWindow(window);
        List<Map<String, Object>> trend = ragRepo.getTrend(range[0], range[1], granularity, collection);
        return ResponseEntity.ok(trend);
    }

    // ── Paginated query list ──────────────────────────────────────────────────

    @GetMapping("/queries")
    public ResponseEntity<?> listQueries(
            @RequestParam(defaultValue = "24h") String window,
            @RequestParam(required = false) String collection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (ragRepo == null) return ResponseEntity.ok(Map.of("items", List.of(), "total", 0));

        int clampedSize = Math.min(100, Math.max(1, size));
        int offset = page * clampedSize;
        Instant[] range = parseWindow(window);

        List<RagQuery> queries = ragRepo.listQueries(range[0], range[1], collection, offset, clampedSize);
        long total = ragRepo.countQueries(range[0], range[1], collection);

        List<Map<String, Object>> items = queries.stream().map(this::toQueryMap).toList();
        return ResponseEntity.ok(Map.of("items", items, "total", total, "page", page, "size", clampedSize));
    }

    // ── Per-query detail ──────────────────────────────────────────────────────

    @GetMapping("/queries/{queryId}")
    public ResponseEntity<?> getQuery(@PathVariable String queryId) {
        if (ragRepo == null) return ResponseEntity.notFound().build();
        return ragRepo.findByQueryId(queryId)
            .map(q -> ResponseEntity.ok(toQueryDetailMap(q)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Collections ───────────────────────────────────────────────────────────

    @GetMapping("/collections")
    public ResponseEntity<?> getCollections(
            @RequestParam(defaultValue = "7d") String window) {
        if (ragRepo == null) return ResponseEntity.ok(List.of());
        Instant[] range = parseWindow(window);
        List<Map<String, Object>> breakdown = ragRepo.getCollectionBreakdown(range[0], range[1]);
        return ResponseEntity.ok(breakdown);
    }

    // ── Semantic clusters ─────────────────────────────────────────────────────

    @GetMapping("/clusters")
    public ResponseEntity<?> getClusters(
            @RequestParam(defaultValue = "7d") String window,
            @RequestParam(required = false) String collection) {
        if (ragRepo == null || clusteringService == null) return ResponseEntity.ok(List.of());
        Instant[] range = parseWindow(window);
        List<Map<String, Object>> clusters = clusteringService.cluster(range[0], range[1], collection);
        return ResponseEntity.ok(clusters);
    }

    // ── Embedding drift ───────────────────────────────────────────────────────

    @GetMapping("/drift")
    public ResponseEntity<?> getDrift(
            @RequestParam(defaultValue = "30d") String window,
            @RequestParam(required = false) String collection) {
        if (ragRepo == null) return ResponseEntity.ok(List.of());
        Instant[] range = parseWindow(window);
        List<Map<String, Object>> snapshots = ragRepo.getDriftSnapshots(range[0], range[1], collection);
        return ResponseEntity.ok(snapshots);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Instant[] parseWindow(String window) {
        Instant now = Instant.now();
        Instant from = switch (window) {
            case "1h"  -> now.minus(1,  ChronoUnit.HOURS);
            case "6h"  -> now.minus(6,  ChronoUnit.HOURS);
            case "24h" -> now.minus(24, ChronoUnit.HOURS);
            case "7d"  -> now.minus(7,  ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            case "90d" -> now.minus(90, ChronoUnit.DAYS);
            default    -> now.minus(24, ChronoUnit.HOURS);
        };
        return new Instant[]{from, now};
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private Map<String, Object> toQueryMap(RagQuery q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("queryId",          q.queryId());
        m.put("runId",            q.runId());
        m.put("spanId",           q.spanId());
        m.put("query",            q.query());
        m.put("collection",       q.collection());
        m.put("latencyMs",        q.latencyMs());
        m.put("chunkCount",       q.chunkCount());
        m.put("topK",             q.topK());
        m.put("contextPrecision", q.contextPrecision());
        m.put("contextRecall",    q.contextRecall());
        m.put("faithfulness",     q.faithfulness());
        m.put("answerRelevancy",  q.answerRelevancy());
        m.put("metadata",         q.metadata());
        return m;
    }

    private Map<String, Object> toQueryDetailMap(RagQuery q) {
        Map<String, Object> m = toQueryMap(q);
        m.put("retrievedChunks",  parseChunks(q.retrievedChunks()));
        m.put("similarityScores", parseScores(q.similarityScores()));
        return m;
    }

    private static List<String> parseChunks(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String trimmed = raw.strip();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]"))   trimmed = trimmed.substring(0, trimmed.length() - 1);
        return Arrays.stream(trimmed.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static List<Double> parseScores(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            String trimmed = raw.strip().replaceAll("[\\[\\]]", "");
            return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Double::parseDouble)
                .toList();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    private static List<Map<String, Object>> toCollectionList(List<Map<String, Object>> breakdown) {
        return breakdown.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>(row);
            return m;
        }).toList();
    }

    private static Map<String, Object> fallbackMetrics() {
        return Map.ofEntries(
            Map.entry("queryCount",           (Object) 0L),
            Map.entry("avgContextPrecision",  0.0),
            Map.entry("avgContextRecall",     0.0),
            Map.entry("avgFaithfulness",      0.0),
            Map.entry("avgAnswerRelevancy",   0.0),
            Map.entry("hitRate",              0.0),
            Map.entry("avgLatencyMs",         0L),
            Map.entry("p50LatencyMs",         0L),
            Map.entry("p95LatencyMs",         0L),
            Map.entry("p99LatencyMs",         0L),
            Map.entry("avgChunkCount",        0.0),
            Map.entry("latencyDistribution",  (Object) List.of()),
            Map.entry("topQueries",           (Object) List.of()),
            Map.entry("collections",          (Object) List.of())
        );
    }
}
