package com.chorus.observe.persistence;

import com.chorus.observe.service.OtlpIngestionService.OtlpSpan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionQueueRepositoryTest {

    private IngestionQueueRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryIngestionQueueRepository();
    }

    @Test
    void shouldEnqueueAndDequeueBatchesCorrectly() {
        OtlpSpan span1 = new OtlpSpan(
            "t1", "s1", "llm-call", Instant.now(), Instant.now(),
            1, 1, Map.of("key", "val"), List.of(), "p1"
        );
        OtlpSpan span2 = new OtlpSpan(
            "t1", "s2", "tool-call", Instant.now(), Instant.now(),
            2, 1, Map.of("key2", "val2"), List.of(), "s1"
        );

        // Verify enqueue batch
        repository.enqueue(List.of(span1, span2));

        // Verify batch fetch
        List<IngestionQueueRepository.QueueRecord> batch = repository.fetchBatch(10);
        assertThat(batch).hasSize(2);
        assertThat(batch.get(0).span().spanId()).isEqualTo("s1");
        assertThat(batch.get(1).span().spanId()).isEqualTo("s2");

        // Verify dequeue
        List<String> ids = List.of(batch.get(0).queueId(), batch.get(1).queueId());
        repository.dequeue(ids);

        // Verify queue is now empty
        List<IngestionQueueRepository.QueueRecord> emptyBatch = repository.fetchBatch(10);
        assertThat(emptyBatch).isEmpty();
    }
}
