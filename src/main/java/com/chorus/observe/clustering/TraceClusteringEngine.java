package com.chorus.observe.clustering;

import com.chorus.observe.clustering.EmbeddingClusterer.ClusterResult;
import com.chorus.observe.clustering.EmbeddingClusterer.LabeledVector;
import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import com.chorus.observe.embedding.EmbeddingInvoker;
import com.chorus.observe.service.AgentInvocationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Enterprise-grade trace clustering engine.
 * <p>
 * Pipeline:
 * <ol>
 *   <li><b>Embed:</b> Generate embedding vectors for trace text via {@link EmbeddingInvoker}</li>
 *   <li><b>Cluster:</b> Run DBSCAN-style density clustering on embeddings</li>
 *   <li><b>Label:</b> Auto-generate cluster labels from member samples</li>
 *   <li><b>Persist:</b> Store clusters in {@code trace_clusters} table</li>
 * </ol>
 */
public class TraceClusteringEngine {

    private static final Logger LOG = LoggerFactory.getLogger(TraceClusteringEngine.class);

    private final TraceEmbeddingRepository embeddingRepository;
    private final TraceClusterRepository traceClusterRepository;
    private final LlmCallRepository llmCallRepository;
    private final RunRepository runRepository;
    private final EmbeddingInvoker embeddingInvoker;
    private final ObjectMapper mapper;

    public TraceClusteringEngine(
            @NonNull TraceEmbeddingRepository embeddingRepository,
            @NonNull TraceClusterRepository traceClusterRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull RunRepository runRepository,
            @NonNull EmbeddingInvoker embeddingInvoker,
            @NonNull ObjectMapper mapper
    ) {
        this.embeddingRepository = Objects.requireNonNull(embeddingRepository);
        this.traceClusterRepository = Objects.requireNonNull(traceClusterRepository);
        this.llmCallRepository = Objects.requireNonNull(llmCallRepository);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.embeddingInvoker = Objects.requireNonNull(embeddingInvoker);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Generate embeddings for all traces in a time window.
     *
     * @param periodStart start of window
     * @param periodEnd   end of window
     * @param model       embedding model identifier
     * @param agentConfig config for the embedding endpoint
     * @return number of embeddings generated
     */
    private static final int EMBEDDING_PARALLELISM = 8;

    public int generateEmbeddings(
            @NonNull Instant periodStart,
            @NonNull Instant periodEnd,
            @NonNull String model,
            @NonNull Map<String, Object> agentConfig
    ) {
        List<Run> runs = runRepository.findAll(new RunRepository.RunQuery(
            null, null, null, null, null, periodStart, periodEnd, null, null, null, "start_time", "DESC", 10000, 0));

        // Collect all (run, text) pairs first to enable parallel embedding generation
        record EmbedTask(@NonNull Run run, @NonNull String text) {}
        List<EmbedTask> tasks = new ArrayList<>();
        for (Run run : runs) {
            List<LlmCall> calls = llmCallRepository.findByRunId(run.runId());
            if (calls.isEmpty()) continue;

            String text = calls.stream()
                .map(c -> Objects.toString(c.prompt(), "") + " " + Objects.toString(c.completion(), ""))
                .collect(Collectors.joining(" "));

            if (!text.isBlank()) {
                tasks.add(new EmbedTask(run, text.trim()));
            }
        }

        // Parallel embedding invocation with bounded concurrency
        Semaphore semaphore = new Semaphore(EMBEDDING_PARALLELISM);
        List<CompletableFuture<TraceEmbedding>> futures = new ArrayList<>();

        for (EmbedTask task : tasks) {
            CompletableFuture<TraceEmbedding> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Embedding generation interrupted", e);
                }
                try {
                    float[] vector = fetchEmbedding(task.text(), model, agentConfig);
                    String embeddingId = "emb-" + task.run().runId();
                    return new TraceEmbedding(
                        embeddingId, task.run().runId(), null, model, vector, task.text(), Map.of(), Instant.now());
                } finally {
                    semaphore.release();
                }
            });
            futures.add(future);
        }

        int count = 0;
        for (CompletableFuture<TraceEmbedding> future : futures) {
            try {
                TraceEmbedding embedding = future.join();
                embeddingRepository.save(embedding);
                count++;
            } catch (Exception e) {
                LOG.warn("Failed to embed run", e);
            }
        }

        LOG.info("Generated {} embeddings for period {} to {}", count, periodStart, periodEnd);
        return count;
    }

    /**
     * Cluster existing embeddings and persist results.
     *
     * @param model          embedding model to cluster (filters embeddings)
     * @param minSimilarity  cosine similarity threshold (0.0–1.0)
     * @param minPoints      minimum points to form a cluster
     * @param periodStart    cluster period start
     * @param periodEnd      cluster period end
     * @return clustering result
     */
    private static final int MAX_EMBEDDINGS_PER_CLUSTER = 50_000;

    public @NonNull ClusteringReport clusterTraces(
            @NonNull String model,
            double minSimilarity,
            int minPoints,
            @NonNull Instant periodStart,
            @NonNull Instant periodEnd
    ) {
        List<TraceEmbedding> embeddings = embeddingRepository.findByModel(model, MAX_EMBEDDINGS_PER_CLUSTER);
        if (embeddings.size() >= MAX_EMBEDDINGS_PER_CLUSTER) {
            LOG.warn("Clustering capped at {} embeddings for model {}. Consider narrowing time window or increasing heap.",
                MAX_EMBEDDINGS_PER_CLUSTER, model);
        }
        if (embeddings.size() < minPoints) {
            LOG.warn("Not enough embeddings to cluster: {} < {}", embeddings.size(), minPoints);
            return new ClusteringReport(0, 0, Map.of());
        }

        List<LabeledVector> vectors = embeddings.stream()
            .map(e -> new LabeledVector(e.runId(), e.vector()))
            .toList();

        EmbeddingClusterer clusterer = new EmbeddingClusterer(minSimilarity, minPoints);
        ClusterResult result = clusterer.cluster(vectors);

        // Generate labels and persist clusters
        int clusterNum = 1;
        for (Map.Entry<String, List<String>> entry : result.clusters().entrySet()) {
            List<String> runIds = entry.getValue();
            String label = generateClusterLabel(runIds);
            String description = generateClusterDescription(runIds);

            // Aggregate stats
            double avgScore = 0.0;
            BigDecimal avgCost = BigDecimal.ZERO;
            int runCount = 0;
            for (String runId : runIds) {
                Optional<Run> runOpt = runRepository.findById(runId);
                if (runOpt.isPresent()) {
                    Run run = runOpt.get();
                    runCount++;
                    avgCost = avgCost.add(run.totalCost());
                }
            }
            if (runCount > 0) {
                avgCost = avgCost.divide(BigDecimal.valueOf(runCount), 8, java.math.RoundingMode.HALF_UP);
            }

            TraceCluster cluster = new TraceCluster(
                "cluster-" + model + "-" + clusterNum++,
                label, description, runIds.size(), avgScore, avgCost,
                periodStart, periodEnd,
                Map.of("runIds", runIds, "memberCount", runIds.size()),
                Instant.now()
            );
            traceClusterRepository.save(cluster);
        }

        LOG.info("Clustered {} embeddings into {} clusters ({} noise)",
            embeddings.size(), result.clusters().size(), result.noise().size());

        return new ClusteringReport(result.clusters().size(), result.noise().size(), result.clusters());
    }

    private float[] fetchEmbedding(@NonNull String text, @NonNull String model, @NonNull Map<String, Object> agentConfig) {
        try {
            return embeddingInvoker.embed(model, text);
        } catch (Exception e) {
            LOG.warn("Embedding invocation failed for model {}: {}", model, e.getMessage());
            throw new RuntimeException("Embedding failed for model " + model, e);
        }
    }

    private @NonNull String generateClusterLabel(@NonNull List<String> runIds) {
        // Sample up to 5 runs for labeling
        List<String> samples = runIds.stream().limit(5).toList();
        StringBuilder sb = new StringBuilder("Cluster of " + runIds.size() + " runs");
        for (String runId : samples) {
            Optional<Run> runOpt = runRepository.findById(runId);
            runOpt.ifPresent(run -> sb.append(" | ").append(run.agentId()));
        }
        return sb.toString();
    }

    private @NonNull String generateClusterDescription(@NonNull List<String> runIds) {
        return "Auto-generated cluster containing " + runIds.size() + " trace runs.";
    }

    public record ClusteringReport(
        int clusterCount,
        int noiseCount,
        @NonNull Map<String, List<String>> clusters
    ) {}
}
