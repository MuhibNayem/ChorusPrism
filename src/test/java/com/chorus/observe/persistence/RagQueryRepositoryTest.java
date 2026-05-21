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
            Map.of("source", "wiki")
        );

        repository.save(query);

        var found = repository.findByRunId("run-1");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).query()).isEqualTo("What is RAG?");
    }
}
