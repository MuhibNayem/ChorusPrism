package com.chorus.observe.service;

import com.chorus.observe.clustering.EmbeddingClusterer;
import com.chorus.observe.clustering.EmbeddingClusterer.LabeledVector;
import com.chorus.observe.embedding.EmbeddingInvoker;
import com.chorus.observe.persistence.RagQueryRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Semantic query clustering for the RAG cluster map.
 * <p>
 * Uses DBSCAN (via {@link EmbeddingClusterer} + VP-tree) on cosine-embedded query texts.
 * Results are returned directly; the caller is responsible for caching/serialising.
 * <p>
 * Embedding calls are batched and async. Queries without embeddings are embedded on first
 * cluster request and the embedding is persisted back to the database for reuse.
 */
public class RagQueryClusteringService {

    private static final Logger LOG = LoggerFactory.getLogger(RagQueryClusteringService.class);
    private static final int MAX_QUERIES_TO_EMBED = 500;
    private static final double MIN_SIMILARITY = 0.80;
    private static final int MIN_CLUSTER_POINTS = 2;

    private final RagQueryRepository ragQueryRepository;
    private final @Nullable EmbeddingInvoker embeddingInvoker;
    private final @Nullable String embeddingModel;
    private final ExecutorService executor;

    public RagQueryClusteringService(
            @NonNull RagQueryRepository ragQueryRepository,
            @Nullable EmbeddingInvoker embeddingInvoker,
            @Nullable String embeddingModel) {
        this.ragQueryRepository = Objects.requireNonNull(ragQueryRepository);
        this.embeddingInvoker = embeddingInvoker;
        this.embeddingModel = embeddingModel != null ? embeddingModel : "text-embedding-3-small";
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rag-embedder");
            t.setDaemon(true);
            return t;
        });
        LOG.info("RagQueryClusteringService started. Embedding: {}",
            embeddingInvoker != null ? "configured (model=" + this.embeddingModel + ")" : "not configured");
    }

    /**
     * Returns cluster assignments for queries in the given time window.
     * Each entry in the result list is a cluster: { label, count, representative_query, query_ids[] }.
     */
    /**
     * Returns cluster assignments for queries in the given time window.
     * <p>
     * When {@code EmbeddingInvoker} is configured, async embeddings are computed
     * for new queries and VP-tree DBSCAN clustering is applied. Until embeddings
     * are available, keyword-based fallback grouping is returned immediately.
     */
    public @NonNull List<Map<String, Object>> cluster(
            @NonNull Instant from, @NonNull Instant to,
            @Nullable String collection) {
        // Always kick off async embedding for new queries (no-op if not configured)
        if (embeddingInvoker != null) {
            embedMissingAsync(from, to);
        }

        List<Map<String, Object>> topQueries =
            ragQueryRepository.getTopQueries(from, to, collection, 200);
        if (topQueries.isEmpty()) return Collections.emptyList();

        // Try DBSCAN with in-memory embedding call for small batches (≤50 distinct queries)
        if (embeddingInvoker != null && topQueries.size() <= 50) {
            List<LabeledVector> points = new ArrayList<>();
            for (Map<String, Object> q : topQueries) {
                String text = (String) q.get("query");
                if (text == null) continue;
                try {
                    float[] vec = embeddingInvoker.embed(embeddingModel, text);
                    points.add(new LabeledVector(text, vec));
                } catch (Exception e) {
                    LOG.debug("Embedding failed for '{}': {}", text, e.getMessage());
                }
            }
            if (points.size() >= MIN_CLUSTER_POINTS) {
                EmbeddingClusterer clusterer = new EmbeddingClusterer(MIN_SIMILARITY, MIN_CLUSTER_POINTS);
                EmbeddingClusterer.ClusterResult result = clusterer.cluster(points);
                return formatClusterResult(result, topQueries);
            }
        }

        return buildFallbackClusters(from, to, collection);
    }

    private void embedMissingAsync(@NonNull Instant from, @NonNull Instant to) {
        executor.submit(() -> {
            try {
                List<Map<String, Object>> missing =
                    ragQueryRepository.findQueriesNeedingEmbedding(from, to, 100);
                for (Map<String, Object> row : missing) {
                    String queryId = (String) row.get("query_id");
                    String text = (String) row.get("query_text");
                    if (queryId == null || text == null || embeddingInvoker == null) continue;
                    try {
                        float[] vec = embeddingInvoker.embed(embeddingModel, text);
                        ragQueryRepository.saveQueryEmbedding(queryId, vec);
                    } catch (Exception e) {
                        LOG.debug("Failed to embed query {}: {}", queryId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Async embedding batch failed: {}", e.getMessage());
            }
        });
    }

    private @NonNull List<Map<String, Object>> formatClusterResult(
            EmbeddingClusterer.@NonNull ClusterResult result,
            @NonNull List<Map<String, Object>> topQueries) {
        Map<String, Long> queryCounts = topQueries.stream()
            .filter(q -> q.get("query") != null)
            .collect(Collectors.toMap(
                q -> (String) q.get("query"),
                q -> ((Number) q.getOrDefault("count", 1L)).longValue(),
                (a, b) -> a
            ));

        List<Map<String, Object>> clusters = new ArrayList<>();

        // Named clusters (cluster-1, cluster-2, …)
        for (Map.Entry<String, List<String>> e : result.clusters().entrySet()) {
            String clusterKey = e.getKey();
            List<String> members = e.getValue();

            String representative = members.stream()
                .max(Comparator.comparingLong(q -> queryCounts.getOrDefault(q, 0L)))
                .orElse(members.get(0));

            long totalCount = members.stream()
                .mapToLong(q -> queryCounts.getOrDefault(q, 1L))
                .sum();

            Map<String, Object> cluster = new LinkedHashMap<>();
            cluster.put("clusterId", clusterKey);
            cluster.put("label", "Cluster " + clusterKey.replace("cluster-", ""));
            cluster.put("representativeQuery", representative);
            cluster.put("memberCount", members.size());
            cluster.put("totalQueryCount", totalCount);
            cluster.put("members", members.size() > 10 ? members.subList(0, 10) : members);
            clusters.add(cluster);
        }

        // Noise points
        if (!result.noise().isEmpty()) {
            Map<String, Object> noise = new LinkedHashMap<>();
            noise.put("clusterId", "noise");
            noise.put("label", "Other");
            noise.put("representativeQuery", result.noise().get(0));
            noise.put("memberCount", result.noise().size());
            noise.put("totalQueryCount", result.noise().size());
            noise.put("members", result.noise().size() > 5 ? result.noise().subList(0, 5) : result.noise());
            clusters.add(noise);
        }

        clusters.sort(Comparator.comparingLong(c -> -((Number) c.get("totalQueryCount")).longValue()));
        return clusters;
    }

    /**
     * Keyword-based fallback when no embeddings are available — clusters by first word.
     */
    private @NonNull List<Map<String, Object>> buildFallbackClusters(
            @NonNull Instant from, @NonNull Instant to, @Nullable String collection) {
        List<Map<String, Object>> top = ragQueryRepository.getTopQueries(from, to, collection, 100);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (Map<String, Object> q : top) {
            String text = (String) q.get("query");
            if (text == null) continue;
            String firstWord = text.toLowerCase().split("\\s+")[0];
            groups.computeIfAbsent(firstWord, k -> new ArrayList<>()).add(text);
        }
        List<Map<String, Object>> clusters = new ArrayList<>();
        int idx = 1;
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("clusterId", "cluster_" + idx++);
            c.put("label", capitalize(e.getKey()) + " queries");
            c.put("representativeQuery", e.getValue().get(0));
            c.put("memberCount", e.getValue().size());
            c.put("totalQueryCount", e.getValue().size());
            c.put("members", e.getValue());
            clusters.add(c);
        }
        return clusters;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static @Nullable float[] parseEmbeddingJson(@NonNull String json) {
        try {
            String trimmed = json.trim().replaceAll("[\\[\\]]", "");
            String[] parts = trimmed.split(",");
            float[] vec = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Float.parseFloat(parts[i].trim());
            }
            return vec;
        } catch (Exception e) {
            return null;
        }
    }
}
