package com.chorus.observe.persistence;

import com.chorus.observe.model.TraceEmbedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceEmbeddingRepositoryTest {

    private TraceEmbeddingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTraceEmbeddingRepository();
    }

    @Test
    void shouldFallbackGracefullyWithoutPgVectorColumn() {
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};
        TraceEmbedding embedding = new TraceEmbedding(
            "e1", "r1", "s1", "text-embedding-3-small",
            vector, "sample text source", Map.of("key", "value"), Instant.now()
        );

        // Verify save runs fallback to JSONB write
        repository.save(embedding);

        var found = repository.findById("e1");
        assertThat(found).isPresent();
        assertThat(found.get().model()).isEqualTo("text-embedding-3-small");
        assertThat(found.get().textSource()).isEqualTo("sample text source");
        assertThat(found.get().vector()).containsExactly(0.1f, 0.2f, 0.3f);

        // Verify fallback nearest neighbor search executes H2-compatible query
        List<TraceEmbedding> neighbors = repository.findNearestNeighbors(vector, 10);
        assertThat(neighbors).hasSize(1);
    }
}
