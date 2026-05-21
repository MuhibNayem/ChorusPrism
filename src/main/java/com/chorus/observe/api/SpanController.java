package com.chorus.observe.api;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.Span;
import com.chorus.observe.model.ToolCall;
import com.chorus.observe.service.SpanService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * REST API v1 for spans, LLM calls, and tool calls.
 */
@RestController
@RequestMapping("/api/v1/runs/{runId}")
public class SpanController {

    private final SpanService spanService;

    public SpanController(@NonNull SpanService spanService) {
        this.spanService = Objects.requireNonNull(spanService);
    }

    @GetMapping("/spans")
    public ResponseEntity<List<Span>> getSpans(@PathVariable @NonNull String runId) {
        return ResponseEntity.ok(spanService.getSpansForRun(runId));
    }

    @GetMapping("/llm-calls")
    public ResponseEntity<List<LlmCall>> getLlmCalls(@PathVariable @NonNull String runId) {
        return ResponseEntity.ok(spanService.getLlmCallsForRun(runId));
    }

    @GetMapping("/tool-calls")
    public ResponseEntity<List<ToolCall>> getToolCalls(@PathVariable @NonNull String runId) {
        return ResponseEntity.ok(spanService.getToolCallsForRun(runId));
    }
}
