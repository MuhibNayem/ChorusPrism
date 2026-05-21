package com.chorus.observe.persistence;

import com.chorus.observe.model.RagQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC repository for RAG queries.
 */
public class RagQueryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<RagQuery> rowMapper;

    public RagQueryRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RagQueryRowMapper(mapper);
    }

    public void save(@NonNull RagQuery query) {
        String sql = """
            INSERT INTO rag_queries (query_id, span_id, run_id, query_text, retrieved_chunks, similarity_scores, latency_ms, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (query_id) DO UPDATE SET
                span_id = EXCLUDED.span_id,
                run_id = EXCLUDED.run_id,
                query_text = EXCLUDED.query_text,
                retrieved_chunks = EXCLUDED.retrieved_chunks,
                similarity_scores = EXCLUDED.similarity_scores,
                latency_ms = EXCLUDED.latency_ms,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            query.queryId(), query.spanId(), query.runId(), query.query(),
            query.retrievedChunks(), query.similarityScores(), query.latencyMs(),
            toJson(query.metadata())
        );
    }

    public @NonNull List<RagQuery> findByRunId(@NonNull String runId) {
        return jdbc.query(
            "SELECT * FROM rag_queries WHERE run_id = ? ORDER BY query_id ASC",
            rowMapper, runId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class RagQueryRowMapper implements RowMapper<RagQuery> {
        private final ObjectMapper mapper;

        RagQueryRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public RagQuery mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new RagQuery(
                    rs.getString("query_id"),
                    rs.getString("span_id"),
                    rs.getString("run_id"),
                    rs.getString("query_text"),
                    rs.getString("retrieved_chunks"),
                    rs.getString("similarity_scores"),
                    rs.getLong("latency_ms"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {})
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
