package com.chorus.observe.store;

import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.Span;
import com.chorus.observe.model.ToolCall;
import com.chorus.observe.persistence.LlmCallRepository;
import com.chorus.observe.persistence.SpanRepository;
import com.chorus.observe.persistence.ToolCallRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL-backed {@link SpanStore}.
 * Uses the existing JDBC repositories for ACID span persistence.
 * <p>
 * All save operations are atomic per batch — if any span in a batch fails,
 * the entire batch throws {@link SpanStoreException} so callers can retry
 * or handle the failure explicitly. No silent data loss.
 */
public class PostgresSpanStore implements SpanStore {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresSpanStore.class);

    private final SpanRepository spanRepository;
    private final LlmCallRepository llmCallRepository;
    private final ToolCallRepository toolCallRepository;
    private final DataSource dataSource;

    public PostgresSpanStore(
            @NonNull SpanRepository spanRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull ToolCallRepository toolCallRepository,
            @Nullable DataSource dataSource) {
        this.spanRepository = Objects.requireNonNull(spanRepository);
        this.llmCallRepository = Objects.requireNonNull(llmCallRepository);
        this.toolCallRepository = Objects.requireNonNull(toolCallRepository);
        this.dataSource = dataSource;
    }

    @Override
    public void saveSpans(@NonNull List<Span> spans) {
        if (spans.isEmpty()) return;
        try {
            spanRepository.saveAll(spans);
        } catch (Exception e) {
            LOG.error("Failed to batch save {} spans", spans.size(), e);
            throw new SpanStoreException("Span batch save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveLlmCalls(@NonNull List<LlmCall> calls) {
        if (calls.isEmpty()) return;
        try {
            llmCallRepository.saveAll(calls);
        } catch (Exception e) {
            LOG.error("Failed to batch save {} LLM calls", calls.size(), e);
            throw new SpanStoreException("LLM call batch save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveToolCalls(@NonNull List<ToolCall> calls) {
        if (calls.isEmpty()) return;
        try {
            toolCallRepository.saveAll(calls);
        } catch (Exception e) {
            LOG.error("Failed to batch save {} tool calls", calls.size(), e);
            throw new SpanStoreException("Tool call batch save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public @NonNull List<Span> findSpansByRunId(@NonNull String runId) {
        return spanRepository.findByRunId(runId);
    }

    @Override
    public @NonNull List<LlmCall> findLlmCallsByRunId(@NonNull String runId) {
        return llmCallRepository.findByRunId(runId);
    }

    @Override
    public @NonNull List<ToolCall> findToolCallsByRunId(@NonNull String runId) {
        return toolCallRepository.findByRunId(runId);
    }

    @Override
    public boolean isHealthy() {
        if (dataSource == null) return true;
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }
}
