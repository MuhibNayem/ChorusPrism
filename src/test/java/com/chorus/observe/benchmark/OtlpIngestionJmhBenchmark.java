package com.chorus.observe.benchmark;

import com.chorus.observe.model.Span;
import com.chorus.observe.persistence.*;
import com.chorus.observe.service.OtlpIngestionService;
import com.chorus.observe.service.OtlpIngestionService.OtlpSpan;
import com.chorus.observe.store.PostgresSpanStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH micro-benchmark for OTLP ingestion throughput.
 * Run with: ./gradlew :chorus-observe-server:jmh
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class OtlpIngestionJmhBenchmark {

    private OtlpIngestionService ingestionService;
    private List<OtlpSpan> batch;

    @Setup
    public void setup() {
        ObjectMapper mapper = new ObjectMapper();
        var runRepo = new InMemoryRunRepository();
        var spanRepo = new InMemorySpanRepository();
        var llmRepo = new InMemoryLlmCallRepository();
        var toolRepo = new InMemoryToolCallRepository();
        var spanStore = new PostgresSpanStore(spanRepo, llmRepo, toolRepo, null);
        ingestionService = new OtlpIngestionService(runRepo, spanStore, mapper);

        Instant base = Instant.now();

        batch = java.util.stream.IntStream.range(0, 100)
            .mapToObj(i -> new OtlpSpan(
                "trace-1",
                "span-" + i,
                "llm.call",
                base.plusMillis(i),
                base.plusMillis(i + 2),
                0,
                1,
                Map.of(
                    "chorus.run_id", "run-bench",
                    "gen_ai.system", "openai",
                    "gen_ai.request.model", "gpt-4o",
                    "gen_ai.usage.input_tokens", 100,
                    "gen_ai.usage.output_tokens", 50
                ),
                List.of(),
                ""
            ))
            .toList();
    }

    @Benchmark
    public void ingestBatchOf100Spans() {
        ingestionService.ingestSpans(batch);
    }
}
