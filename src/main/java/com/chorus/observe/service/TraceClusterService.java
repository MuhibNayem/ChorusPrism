package com.chorus.observe.service;

import com.chorus.observe.clustering.TraceClusteringEngine;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.TraceCluster;
import com.chorus.observe.persistence.TraceClusterRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for trace cluster management and automated cluster analysis.
 */
public class TraceClusterService {

    private final TraceClusterRepository traceClusterRepository;
    private final TraceClusteringEngine clusteringEngine;

    public TraceClusterService(@NonNull TraceClusterRepository traceClusterRepository, @Nullable TraceClusteringEngine clusteringEngine) {
        this.traceClusterRepository = Objects.requireNonNull(traceClusterRepository);
        this.clusteringEngine = clusteringEngine;
    }

    public @NonNull TraceCluster createCluster(@NonNull String label, @Nullable String description, int runCount, @Nullable Double avgScore, @Nullable BigDecimal avgCost, @NonNull Instant periodStart, @NonNull Instant periodEnd, @NonNull Map<String, Object> metadata) {
        String clusterId = "cluster-" + UUID.randomUUID().toString().substring(0, 8);
        TraceCluster cluster = new TraceCluster(clusterId, label, description, runCount, avgScore, avgCost, periodStart, periodEnd, metadata, Instant.now());
        traceClusterRepository.save(cluster);
        return cluster;
    }

    public @NonNull Optional<TraceCluster> getCluster(@NonNull String clusterId) {
        return traceClusterRepository.findById(clusterId);
    }

    public @NonNull List<TraceCluster> listClusters() {
        return traceClusterRepository.findAll();
    }

    public @NonNull PagedResult<TraceCluster> listClusters(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(traceClusterRepository.findAll(size, offset), traceClusterRepository.count(), page, size);
    }

    public @NonNull List<TraceCluster> listClustersByPeriod(@NonNull Instant start, @NonNull Instant end) {
        return traceClusterRepository.findByPeriod(start, end);
    }

    public @NonNull PagedResult<TraceCluster> listClustersByPeriod(@NonNull Instant start, @NonNull Instant end, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(traceClusterRepository.findByPeriod(start, end, size, offset), traceClusterRepository.countByPeriod(start, end), page, size);
    }

    /**
     * Run the automated clustering pipeline: embed → cluster → label → persist.
     *
     * @param periodStart   analysis window start
     * @param periodEnd     analysis window end
     * @param model         embedding model
     * @param agentConfig   embedding endpoint config
     * @param minSimilarity cosine similarity threshold
     * @param minPoints     minimum points per cluster
     * @return clustering report
     * @throws IllegalStateException if clustering engine is not configured
     */
    public TraceClusteringEngine.@NonNull ClusteringReport analyzeClusters(
            @NonNull Instant periodStart,
            @NonNull Instant periodEnd,
            @NonNull String model,
            @NonNull Map<String, Object> agentConfig,
            double minSimilarity,
            int minPoints
    ) {
        if (clusteringEngine == null) {
            throw new IllegalStateException("Clustering engine is not configured");
        }
        int embedded = clusteringEngine.generateEmbeddings(periodStart, periodEnd, model, agentConfig);
        if (embedded == 0) {
            return new TraceClusteringEngine.ClusteringReport(0, 0, Map.of());
        }
        return clusteringEngine.clusterTraces(model, minSimilarity, minPoints, periodStart, periodEnd);
    }
}
