package com.chorus.observe.persistence;

import com.chorus.observe.model.RagQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagQueryRepositoryTest {

    private RagQueryRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryRagQueryRepository();
    }

    @Test
    void shouldSaveAndFindByRunId() {
        RagQuery query = new RagQuery(
            "q1", "span-1", "run-1",
            "What is RAG?",
            "[chunk1, chunk2]",
            "[0.95, 0.87]",
            120,
            Map.of("source", "wiki"),
            0.91, 1.0, null, null,
            2, "product_docs", 5
        );

        repository.save(query);

        var found = repository.findByRunId("run-1");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).query()).isEqualTo("What is RAG?");
        assertThat(found.get(0).contextPrecision()).isEqualTo(0.91);
        assertThat(found.get(0).collection()).isEqualTo("product_docs");
    }

    @Test
    void shouldComputePrecisionFromSimilarityScores() {
        double precision = RagQuery.computePrecision("[0.90, 0.80, 0.70]");
        assertThat(precision).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldComputeRecallFromSimilarityScores() {
        double recall = RagQuery.computeRecall("[0.90, 0.80, 0.60]");
        // Two of three are >= 0.70 → recall = 2/3 ≈ 0.667
        assertThat(recall).isCloseTo(0.667, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldHandleNullSimilarityScores() {
        double precision = RagQuery.computePrecision(null);
        assertThat(precision).isEqualTo(0.0);
    }

    @Test
    void shouldUseFactoryMethodForIngestion() {
        RagQuery query = RagQuery.ofIngestion(
            "q2", "span-2", "run-2",
            "How does HNSW indexing work?",
            "[chunk_a]",
            "[0.92, 0.85]",
            150,
            Map.of(),
            2, "technical_specs", 5
        );
        assertThat(query.contextPrecision()).isNotNull();
        assertThat(query.contextPrecision()).isGreaterThan(0.0);
        assertThat(query.faithfulness()).isNull();
    }

    @Test
    void shouldUpdateScores() {
        RagQuery query = new RagQuery(
            "q3", "span-3", "run-3",
            "Explain chunking strategies",
            null, "[0.88, 0.75]", 200,
            Map.of(), 0.815, 1.0, null, null, 2, "support_kb", 5
        );
        repository.save(query);
        repository.updateScores("q3", 0.91, 0.87);

        var updated = repository.findByRunId("run-3");
        assertThat(updated.get(0).faithfulness()).isEqualTo(0.91);
        assertThat(updated.get(0).answerRelevancy()).isEqualTo(0.87);
    }
}
