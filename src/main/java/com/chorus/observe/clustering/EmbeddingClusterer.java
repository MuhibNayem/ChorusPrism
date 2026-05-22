package com.chorus.observe.clustering;

import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Density-based clustering (DBSCAN variant) for embedding vectors.
 * <p>
 * Uses cosine similarity instead of Euclidean distance, which is more appropriate
 * for high-dimensional embedding spaces. Points are neighbors if their cosine
 * similarity is >= {@code minSimilarity} (i.e., angular distance is small).
 * <p>
 * Thread-safe and deterministic (given stable input order).
 */
public final class EmbeddingClusterer {

    private final double minSimilarity;
    private final int minPoints;

    public EmbeddingClusterer(double minSimilarity, int minPoints) {
        if (minSimilarity < -1.0 || minSimilarity > 1.0) {
            throw new IllegalArgumentException("minSimilarity must be in [-1, 1]");
        }
        if (minPoints < 1) {
            throw new IllegalArgumentException("minPoints must be >= 1");
        }
        this.minSimilarity = minSimilarity;
        this.minPoints = minPoints;
    }

    /**
     * Cluster a set of labeled vectors.
     *
     * @param points list of (id, vector) pairs
     * @return map from cluster label to list of point ids in that cluster
     */
    public @NonNull ClusterResult cluster(@NonNull List<LabeledVector> points) {
        if (points.isEmpty()) {
            return new ClusterResult(Map.of(), List.of());
        }

        int n = points.size();
        boolean[] visited = new boolean[n];
        int[] clusterIds = new int[n];
        Arrays.fill(clusterIds, -1); // -1 = unassigned, -2 = noise

        int nextClusterId = 0;
        List<String> noise = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (visited[i]) continue;
            visited[i] = true;

            List<Integer> neighbors = regionQuery(points, i);
            if (neighbors.size() < minPoints) {
                clusterIds[i] = -2; // mark as noise temporarily
                continue;
            }

            // Start a new cluster
            expandCluster(points, i, neighbors, visited, clusterIds, nextClusterId);
            nextClusterId++;
        }

        // Collect results
        Map<Integer, List<String>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            if (clusterIds[i] >= 0) {
                clusters.computeIfAbsent(clusterIds[i], k -> new ArrayList<>())
                    .add(points.get(i).id());
            } else if (clusterIds[i] == -2) {
                noise.add(points.get(i).id());
            }
        }

        // Re-label clusters to stable names
        Map<String, List<String>> namedClusters = new LinkedHashMap<>();
        int clusterNum = 1;
        for (List<String> members : clusters.values()) {
            namedClusters.put("cluster-" + clusterNum++, members);
        }

        return new ClusterResult(namedClusters, noise);
    }

    private void expandCluster(
            @NonNull List<LabeledVector> points,
            int coreIdx,
            @NonNull List<Integer> neighbors,
            boolean[] visited,
            int[] clusterIds,
            int clusterLabel
    ) {
        clusterIds[coreIdx] = clusterLabel;
        Deque<Integer> seeds = new ArrayDeque<>(neighbors);

        while (!seeds.isEmpty()) {
            int j = seeds.poll();
            if (!visited[j]) {
                visited[j] = true;
                List<Integer> jNeighbors = regionQuery(points, j);
                if (jNeighbors.size() >= minPoints) {
                    seeds.addAll(jNeighbors);
                }
            }
            if (clusterIds[j] < 0) {
                clusterIds[j] = clusterLabel;
            }
        }
    }

    private @NonNull List<Integer> regionQuery(@NonNull List<LabeledVector> points, int idx) {
        float[] v = points.get(idx).vector();
        List<Integer> neighbors = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (i == idx) {
                neighbors.add(i);
                continue;
            }
            if (cosineSimilarity(v, points.get(i).vector()) >= minSimilarity) {
                neighbors.add(i);
            }
        }
        return neighbors;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same dimensions: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * A labeled vector for clustering.
     */
    public record LabeledVector(@NonNull String id, @NonNull float[] vector) {}

    /**
     * Result of clustering.
     *
     * @param clusters map from cluster name to member ids
     * @param noise    ids that did not belong to any cluster
     */
    public record ClusterResult(@NonNull Map<String, List<String>> clusters, @NonNull List<String> noise) {}
}
