package com.chorus.observe.persistence;

import com.chorus.observe.model.ProvenanceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProvenanceRepositoryTest {

    private ProvenanceRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryProvenanceRepository();
    }

    @Test
    void shouldSaveAndFindByRunId() {
        ProvenanceEntry entry = new ProvenanceEntry(
            "e1", "run-1", "agent-1", "llm_plan",
            "input", "reasoning", "output",
            List.of("parent-1"), Instant.now(), Map.of("key", "value")
        );

        repository.save(entry);

        var found = repository.findByRunId("run-1");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).entryId()).isEqualTo("e1");
        assertThat(found.get(0).parentIds()).containsExactly("parent-1");
    }
}
