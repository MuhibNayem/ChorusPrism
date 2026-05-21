package com.chorus.observe.service;

import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import com.chorus.observe.store.PostgresSpanStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OtlpIngestionServiceTest {

    private OtlpIngestionService ingestionService;
    private RunRepository runRepository;
    private SpanRepository spanRepository;
    private LlmCallRepository llmCallRepository;
    private ToolCallRepository toolCallRepository;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        this.runRepository = new InMemoryRunRepository();
        this.spanRepository = new InMemorySpanRepository();
        this.llmCallRepository = new InMemoryLlmCallRepository();
        this.toolCallRepository = new InMemoryToolCallRepository();
        var spanStore = new PostgresSpanStore(spanRepository, llmCallRepository, toolCallRepository, null);
        this.ingestionService = new OtlpIngestionService(runRepository, spanStore, mapper);
    }

    @Test
    void shouldIngestSpanAndCreateRun() {
        OtlpIngestionService.OtlpSpan otlpSpan = new OtlpIngestionService.OtlpSpan(
            "trace-1", "span-1", "agent.run", Instant.now(), Instant.now(),
            0, 0,
            Map.of("chorus.run_id", "run-1", "chorus.framework", "chorus", "gen_ai.agent.id", "agent-1"),
            List.of(), ""
        );

        ingestionService.ingestSpans(List.of(otlpSpan));

        assertThat(runRepository.findById("run-1")).isPresent();
        assertThat(spanRepository.findByRunId("run-1")).hasSize(1);
    }

    @Test
    void shouldExtractLlmCallFromGenAiAttributes() {
        OtlpIngestionService.OtlpSpan otlpSpan = new OtlpIngestionService.OtlpSpan(
            "trace-1", "span-1", "llm.call", Instant.now(), Instant.now().plusMillis(1500),
            0, 0,
            Map.of(
                "chorus.run_id", "run-1",
                "gen_ai.system", "openai",
                "gen_ai.request.model", "gpt-4o",
                "gen_ai.usage.input_tokens", 120,
                "gen_ai.usage.output_tokens", 80,
                "chorus.cost_usd", 0.003
            ),
            List.of(), ""
        );

        ingestionService.ingestSpans(List.of(otlpSpan));

        List<LlmCall> calls = llmCallRepository.findByRunId("run-1");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).provider()).isEqualTo("openai");
        assertThat(calls.get(0).model()).isEqualTo("gpt-4o");
        assertThat(calls.get(0).inputTokens()).isEqualTo(120);
        assertThat(calls.get(0).outputTokens()).isEqualTo(80);
    }

    @Test
    void shouldExtractToolCallFromAttributes() {
        OtlpIngestionService.OtlpSpan otlpSpan = new OtlpIngestionService.OtlpSpan(
            "trace-1", "span-1", "tool.call", Instant.now(), Instant.now().plusMillis(500),
            0, 0,
            Map.of(
                "chorus.run_id", "run-1",
                "gen_ai.tool.name", "runTests",
                "chorus.tool.args", "{\"module\":\"auth\"}",
                "chorus.tool.result", "47 tests passed"
            ),
            List.of(), ""
        );

        ingestionService.ingestSpans(List.of(otlpSpan));

        List<ToolCall> calls = toolCallRepository.findByRunId("run-1");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).toolName()).isEqualTo("runTests");
        assertThat(calls.get(0).args()).isEqualTo("{\"module\":\"auth\"}");
    }
}
