package com.chorus.observe.service;

import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Micro-benchmark for OTLP ingestion throughput.
 * Target: 10,000 spans/second at <5ms p99 write latency.
 * <p>
 * Note: This is an in-memory benchmark. Real p99 latency requires
 * a production PostgreSQL instance and JMH or Gatling for accurate measurement.
 */
class OtlpIngestionBenchmark {

    @Test
    void shouldIngestOneThousandSpansInUnderOneSecond() {
        ObjectMapper mapper = new ObjectMapper();
        var runRepo = new InMemoryRunRepository();
        var spanRepo = new InMemorySpanRepository();
        var llmRepo = new InMemoryLlmCallRepository();
        var toolRepo = new InMemoryToolCallRepository();
        var spanStore = new com.chorus.observe.store.PostgresSpanStore(spanRepo, llmRepo, toolRepo, null);

        var ingestionService = new OtlpIngestionService(runRepo, spanStore, mapper);

        List<OtlpIngestionService.OtlpSpan> spans = new ArrayList<>();
        Instant base = Instant.now();

        for (int i = 0; i < 1000; i++) {
            spans.add(new OtlpIngestionService.OtlpSpan(
                "trace-1",
                "span-" + i,
                "llm.call",
                base.plusMillis(i),
                base.plusMillis(i + 2),
                0, 0,
                Map.of(
                    "chorus.run_id", "run-bench",
                    "gen_ai.system", "openai",
                    "gen_ai.request.model", "gpt-4o",
                    "gen_ai.usage.input_tokens", 100,
                    "gen_ai.usage.output_tokens", 50
                ),
                List.of(),
                ""
            ));
        }

        long start = System.nanoTime();
        ingestionService.ingestSpans(spans);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Verify data landed
        assertThat(runRepo.findById("run-bench")).isPresent();
        assertThat(spanRepo.findByRunId("run-bench")).hasSize(1000);
        assertThat(llmRepo.findByRunId("run-bench")).hasSize(1000);

        // Assert throughput: 1000 spans in < 1000ms (conservative in-memory target)
        // Real p99 target of <5ms per span requires production PostgreSQL + connection pooling
        assertThat(elapsedMs)
            .withFailMessage("Expected 1000 spans in < 1000ms, took %d ms", elapsedMs)
            .isLessThan(1000);
    }
}
