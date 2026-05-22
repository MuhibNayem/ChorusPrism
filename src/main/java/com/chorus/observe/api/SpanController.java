package com.chorus.observe.api;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.Span;
import com.chorus.observe.model.ToolCall;
import com.chorus.observe.service.SpanService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<PagedResult<Span>> getSpans(
            @PathVariable @NonNull String runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(spanService.getSpansForRun(runId, page, size));
    }

    @GetMapping("/llm-calls")
    public ResponseEntity<PagedResult<LlmCall>> getLlmCalls(
            @PathVariable @NonNull String runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(spanService.getLlmCallsForRun(runId, page, size));
    }

    @GetMapping("/tool-calls")
    public ResponseEntity<PagedResult<ToolCall>> getToolCalls(
            @PathVariable @NonNull String runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(spanService.getToolCallsForRun(runId, page, size));
    }
}
