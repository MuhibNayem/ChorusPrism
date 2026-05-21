package com.chorus.observe.store;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.Span;
import com.chorus.observe.model.ToolCall;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Unified persistence API for high-throughput span data.
 * Implementations may target PostgreSQL, ClickHouse, or both.
 */
public interface SpanStore {

    void saveSpans(@NonNull List<Span> spans);

    void saveLlmCalls(@NonNull List<LlmCall> calls);

    void saveToolCalls(@NonNull List<ToolCall> calls);

    @NonNull List<Span> findSpansByRunId(@NonNull String runId);

    @NonNull List<LlmCall> findLlmCallsByRunId(@NonNull String runId);

    @NonNull List<ToolCall> findToolCallsByRunId(@NonNull String runId);

    /** Returns true if the underlying connection is healthy. */
    boolean isHealthy();
}
