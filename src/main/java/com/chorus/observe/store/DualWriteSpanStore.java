package com.chorus.observe.store;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.Span;
import com.chorus.observe.model.ToolCall;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Writes span data to both a primary and a secondary {@link SpanStore}.
 * Reads are served from the primary store.
 * <p>
 * Use case: migrating from PostgreSQL to ClickHouse, or maintaining
 * a hot backup. The secondary store never blocks the primary.
 */
public class DualWriteSpanStore implements SpanStore {

    private static final Logger LOG = LoggerFactory.getLogger(DualWriteSpanStore.class);

    private final SpanStore primary;
    private final SpanStore secondary;

    public DualWriteSpanStore(@NonNull SpanStore primary, @NonNull SpanStore secondary) {
        this.primary = Objects.requireNonNull(primary);
        this.secondary = Objects.requireNonNull(secondary);
    }

    @Override
    public void saveSpans(@NonNull List<Span> spans) {
        primary.saveSpans(spans);
        try {
            secondary.saveSpans(spans);
        } catch (Exception e) {
            LOG.warn("Secondary span store failed (non-blocking): {}", e.getMessage());
        }
    }

    @Override
    public void saveLlmCalls(@NonNull List<LlmCall> calls) {
        primary.saveLlmCalls(calls);
        try {
            secondary.saveLlmCalls(calls);
        } catch (Exception e) {
            LOG.warn("Secondary LLM call store failed (non-blocking): {}", e.getMessage());
        }
    }

    @Override
    public void saveToolCalls(@NonNull List<ToolCall> calls) {
        primary.saveToolCalls(calls);
        try {
            secondary.saveToolCalls(calls);
        } catch (Exception e) {
            LOG.warn("Secondary tool call store failed (non-blocking): {}", e.getMessage());
        }
    }

    @Override
    public @NonNull List<Span> findSpansByRunId(@NonNull String runId) {
        return primary.findSpansByRunId(runId);
    }

    @Override
    public @NonNull List<LlmCall> findLlmCallsByRunId(@NonNull String runId) {
        return primary.findLlmCallsByRunId(runId);
    }

    @Override
    public @NonNull List<ToolCall> findToolCallsByRunId(@NonNull String runId) {
        return primary.findToolCallsByRunId(runId);
    }

    @Override
    public boolean isHealthy() {
        return primary.isHealthy() && secondary.isHealthy();
    }
}
