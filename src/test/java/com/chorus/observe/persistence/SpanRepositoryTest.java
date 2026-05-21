package com.chorus.observe.persistence;

import com.chorus.observe.model.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpanRepositoryTest {

    private SpanRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemorySpanRepository();
    }

    @Test
    void shouldSaveAndFindByRunId() {
        Span span = new Span(
            "span-1", "run-1", null, "llm.call", Span.Kind.INTERNAL,
            Instant.now(), Instant.now(), Map.of("key", "value"), List.of(), Span.Status.OK
        );

        repository.save(span);

        List<Span> spans = repository.findByRunId("run-1");
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).spanName()).isEqualTo("llm.call");
    }
}
