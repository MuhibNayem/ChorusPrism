package com.chorus.observe.service;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.PagedResult;
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

    public @NonNull List<Span> getSpansForRun(@NonNull String runId, @NonNull String tenantId) {
        if (!spanRepository.runBelongsToTenant(runId, tenantId)) {
            return List.of();
        }
        return spanRepository.findByRunId(runId);
    }

    public @NonNull PagedResult<Span> getSpansForRun(@NonNull String runId, @NonNull String tenantId, int page, int size) {
        if (!spanRepository.runBelongsToTenant(runId, tenantId)) {
            return new PagedResult<>(List.of(), 0, page, size);
        }
        int offset = page * size;
        return new PagedResult<>(spanRepository.findByRunId(runId, size, offset), spanRepository.countByRunId(runId), page, size);
    }

    public @NonNull List<LlmCall> getLlmCallsForRun(@NonNull String runId, @NonNull String tenantId) {
        if (!spanRepository.runBelongsToTenant(runId, tenantId)) {
            return List.of();
        }
        return llmCallRepository.findByRunId(runId);
    }

    public @NonNull PagedResult<LlmCall> getLlmCallsForRun(@NonNull String runId, @NonNull String tenantId, int page, int size) {
        if (!spanRepository.runBelongsToTenant(runId, tenantId)) {
            return new PagedResult<>(List.of(), 0, page, size);
        }
        int offset = page * size;
        return new PagedResult<>(llmCallRepository.findByRunId(runId, size, offset), llmCallRepository.countByRunId(runId), page, size);
    }

    public @NonNull List<ToolCall> getToolCallsForRun(@NonNull String runId, @NonNull String tenantId) {
        if (!spanRepository.runBelongsToTenant(runId, tenantId)) {
            return List.of();
        }
        return toolCallRepository.findByRunId(runId);
    }

    public @NonNull PagedResult<ToolCall> getToolCallsForRun(@NonNull String runId, @NonNull String tenantId, int page, int size) {
        if (!spanRepository.runBelongsToTenant(runId, tenantId)) {
            return new PagedResult<>(List.of(), 0, page, size);
        }
        int offset = page * size;
        return new PagedResult<>(toolCallRepository.findByRunId(runId, size, offset), toolCallRepository.countByRunId(runId), page, size);
    }
}
