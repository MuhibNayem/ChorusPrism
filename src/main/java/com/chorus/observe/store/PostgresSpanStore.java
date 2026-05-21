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
        for (Span span : spans) {
            try {
                spanRepository.save(span);
            } catch (Exception e) {
                LOG.warn("Failed to save span {}: {}", span.spanId(), e.getMessage());
            }
        }
    }

    @Override
    public void saveLlmCalls(@NonNull List<LlmCall> calls) {
        for (LlmCall call : calls) {
            try {
                llmCallRepository.save(call);
            } catch (Exception e) {
                LOG.warn("Failed to save LLM call {}: {}", call.callId(), e.getMessage());
            }
        }
    }

    @Override
    public void saveToolCalls(@NonNull List<ToolCall> calls) {
        for (ToolCall call : calls) {
            try {
                toolCallRepository.save(call);
            } catch (Exception e) {
                LOG.warn("Failed to save tool call {}: {}", call.callId(), e.getMessage());
            }
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
