package com.chorus.observe.clustering;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingClustererTest {

    @Test
    void shouldClusterIdenticalVectorsTogether() {
        List<EmbeddingClusterer.LabeledVector> points = List.of(
            new EmbeddingClusterer.LabeledVector("a", new float[]{1.0f, 0.0f, 0.0f}),
            new EmbeddingClusterer.LabeledVector("b", new float[]{1.0f, 0.0f, 0.0f}),
            new EmbeddingClusterer.LabeledVector("c", new float[]{1.0f, 0.0f, 0.0f})
        );

        EmbeddingClusterer clusterer = new EmbeddingClusterer(0.99, 2);
        EmbeddingClusterer.ClusterResult result = clusterer.cluster(points);

        assertThat(result.clusters()).hasSize(1);
        assertThat(result.clusters().values().iterator().next())
            .containsExactlyInAnyOrder("a", "b", "c");
        assertThat(result.noise()).isEmpty();
    }

    @Test
    void shouldSeparateOrthogonalVectors() {
        List<EmbeddingClusterer.LabeledVector> points = List.of(
            new EmbeddingClusterer.LabeledVector("a", new float[]{1.0f, 0.0f, 0.0f}),
            new EmbeddingClusterer.LabeledVector("b", new float[]{0.0f, 1.0f, 0.0f}),
            new EmbeddingClusterer.LabeledVector("c", new float[]{0.0f, 0.0f, 1.0f})
        );

        EmbeddingClusterer clusterer = new EmbeddingClusterer(0.9, 2);
        EmbeddingClusterer.ClusterResult result = clusterer.cluster(points);

        // Orthogonal vectors have cos_sim = 0, so all should be noise
        assertThat(result.clusters()).isEmpty();
        assertThat(result.noise()).hasSize(3);
    }

    @Test
    void shouldClusterSimilarVectorsWithVPTree() {
        // Generate 2000 points: 1000 near [1,0,0...] and 1000 near [0,1,0...]
        int dim = 128;
        List<EmbeddingClusterer.LabeledVector> points = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            float[] v = new float[dim];
            v[0] = 0.9f + 0.1f * (float) Math.random();
            for (int j = 1; j < dim; j++) {
                v[j] = 0.05f * (float) Math.random();
            }
            points.add(new EmbeddingClusterer.LabeledVector("g1-" + i, v));
        }
        for (int i = 0; i < 1000; i++) {
            float[] v = new float[dim];
            v[1] = 0.9f + 0.1f * (float) Math.random();
            for (int j = 0; j < dim; j++) {
                if (j != 1) v[j] = 0.05f * (float) Math.random();
            }
            points.add(new EmbeddingClusterer.LabeledVector("g2-" + i, v));
        }

        long start = System.currentTimeMillis();
        EmbeddingClusterer clusterer = new EmbeddingClusterer(0.8, 10);
        EmbeddingClusterer.ClusterResult result = clusterer.cluster(points);
        long duration = System.currentTimeMillis() - start;

        // Should complete in reasonable time (< 5 seconds for 2000 points with VP-Tree)
        assertThat(duration).isLessThan(5000);

        // Should find at least 1 cluster
        assertThat(result.clusters()).isNotEmpty();

        // Most points should be in clusters, not noise
        int clusteredCount = result.clusters().values().stream().mapToInt(List::size).sum();
        assertThat(clusteredCount).isGreaterThan(1500);
    }

    @Test
    void shouldHandleEmptyInput() {
        EmbeddingClusterer clusterer = new EmbeddingClusterer(0.8, 2);
        EmbeddingClusterer.ClusterResult result = clusterer.cluster(List.of());
        assertThat(result.clusters()).isEmpty();
        assertThat(result.noise()).isEmpty();
    }

    @Test
    void shouldHandleSinglePoint() {
        List<EmbeddingClusterer.LabeledVector> points = List.of(
            new EmbeddingClusterer.LabeledVector("solo", new float[]{1.0f, 0.0f, 0.0f})
        );

        EmbeddingClusterer clusterer = new EmbeddingClusterer(0.8, 2);
        EmbeddingClusterer.ClusterResult result = clusterer.cluster(points);

        // Single point with minPoints=2 becomes noise
        assertThat(result.clusters()).isEmpty();
        assertThat(result.noise()).containsExactly("solo");
    }

    @Test
    void shouldUseLinearScanForSmallDataset() {
        // 500 points is below VP_TREE_THRESHOLD (1000), so linear scan is used
        List<EmbeddingClusterer.LabeledVector> points = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            float[] v = new float[]{1.0f, 0.0f, (float) (i * 0.001)};
            points.add(new EmbeddingClusterer.LabeledVector("p" + i, v));
        }

        EmbeddingClusterer clusterer = new EmbeddingClusterer(0.95, 5);
        EmbeddingClusterer.ClusterResult result = clusterer.cluster(points);

        // All similar vectors should cluster together
        assertThat(result.clusters()).isNotEmpty();
    }
}
