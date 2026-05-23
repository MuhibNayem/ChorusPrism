package com.chorus.observe.store;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.Span;
import com.chorus.observe.model.ToolCall;
import com.chorus.observe.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpanStoreTest {

    private InMemorySpanRepository spanRepo;
    private InMemoryLlmCallRepository llmRepo;
    private InMemoryToolCallRepository toolRepo;
    private PostgresSpanStore store;

    @BeforeEach
    void setUp() {
        spanRepo = new InMemorySpanRepository();
        llmRepo = new InMemoryLlmCallRepository();
        toolRepo = new InMemoryToolCallRepository();
        store = new PostgresSpanStore(spanRepo, llmRepo, toolRepo, null);
    }

    @Test
    void shouldSaveAndRetrieveSpans() {
        Span span = new Span("s1", "run-1", null, "test", Span.Kind.INTERNAL,
            Instant.now(), Instant.now(), Map.of(), List.of(), Span.Status.OK, null, null);

        store.saveSpans(List.of(span));

        assertThat(store.findSpansByRunId("run-1")).hasSize(1);
        assertThat(spanRepo.findByRunId("run-1")).hasSize(1);
    }

    @Test
    void shouldSaveAndRetrieveLlmCalls() {
        LlmCall call = new LlmCall("c1", "s1", "run-1", "openai", "gpt-4o",
            100, 50, BigDecimal.valueOf(0.01), 1200, "prompt", "completion",
            List.of("stop"), null);

        store.saveLlmCalls(List.of(call));

        assertThat(store.findLlmCallsByRunId("run-1")).hasSize(1);
        assertThat(llmRepo.findByRunId("run-1")).hasSize(1);
    }

    @Test
    void shouldSaveAndRetrieveToolCalls() {
        ToolCall call = new ToolCall("c1", "s1", "run-1", "runTests",
            "{}", "ok", 500, null);

        store.saveToolCalls(List.of(call));

        assertThat(store.findToolCallsByRunId("run-1")).hasSize(1);
        assertThat(toolRepo.findByRunId("run-1")).hasSize(1);
    }

    @Test
    void dualWriteShouldPersistToBothStores() {
        var secondarySpanRepo = new InMemorySpanRepository();
        var secondaryLlmRepo = new InMemoryLlmCallRepository();
        var secondaryToolRepo = new InMemoryToolCallRepository();
        var secondaryStore = new PostgresSpanStore(secondarySpanRepo, secondaryLlmRepo, secondaryToolRepo, null);

        var dualStore = new DualWriteSpanStore(store, secondaryStore);

        Span span = new Span("s1", "run-1", null, "test", Span.Kind.INTERNAL,
            Instant.now(), Instant.now(), Map.of(), List.of(), Span.Status.OK, null, null);
        dualStore.saveSpans(List.of(span));

        assertThat(store.findSpansByRunId("run-1")).hasSize(1);
        assertThat(secondaryStore.findSpansByRunId("run-1")).hasSize(1);
    }
}
