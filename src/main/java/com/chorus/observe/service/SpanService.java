package com.chorus.observe.service;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.Span;
import com.chorus.observe.model.ToolCall;
import com.chorus.observe.persistence.LlmCallRepository;
import com.chorus.observe.persistence.SpanRepository;
import com.chorus.observe.persistence.ToolCallRepository;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Service layer for span and call operations.
 */
public class SpanService {

    private final SpanRepository spanRepository;
    private final LlmCallRepository llmCallRepository;
    private final ToolCallRepository toolCallRepository;

    public SpanService(
            @NonNull SpanRepository spanRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull ToolCallRepository toolCallRepository) {
        this.spanRepository = Objects.requireNonNull(spanRepository);
        this.llmCallRepository = Objects.requireNonNull(llmCallRepository);
        this.toolCallRepository = Objects.requireNonNull(toolCallRepository);
    }

    public @NonNull List<Span> getSpansForRun(@NonNull String runId) {
        return spanRepository.findByRunId(runId);
    }

    public @NonNull List<LlmCall> getLlmCallsForRun(@NonNull String runId) {
        return llmCallRepository.findByRunId(runId);
    }

    public @NonNull List<ToolCall> getToolCallsForRun(@NonNull String runId) {
        return toolCallRepository.findByRunId(runId);
    }
}
