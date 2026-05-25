package com.chorus.observe.persistence;

import com.chorus.observe.model.RagQuery;
import com.chorus.observe.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * JDBC repository for RAG queries.
 * <p>
 * Provides both CRUD operations and pre-aggregated analytics queries designed
 * for sub-second response time on the observability dashboard.
 */
public class RagQueryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<RagQuery> rowMapper;

    public RagQueryRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RagQueryRowMapper(mapper);
    }

    /** For in-memory/testing subclasses that override all methods and never touch jdbc. */
    protected RagQueryRepository(@NonNull ObjectMapper mapper) {
        this.jdbc = null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RagQueryRowMapper(mapper);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void save(@NonNull RagQuery query) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO rag_queries (
                query_id, tenant_id, span_id, run_id, query_text,
                retrieved_chunks, similarity_scores, latency_ms, metadata,
                context_precision, context_recall, faithfulness, answer_relevancy,
                chunk_count, collection, top_k
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (query_id) DO UPDATE SET
                tenant_id         = EXCLUDED.tenant_id,
                span_id           = EXCLUDED.span_id,
                run_id            = EXCLUDED.run_id,
                query_text        = EXCLUDED.query_text,
                retrieved_chunks  = EXCLUDED.retrieved_chunks,
                similarity_scores = EXCLUDED.similarity_scores,
                latency_ms        = EXCLUDED.latency_ms,
                metadata          = EXCLUDED.metadata,
                context_precision = EXCLUDED.context_precision,
                context_recall    = EXCLUDED.context_recall,
                faithfulness      = EXCLUDED.faithfulness,
                answer_relevancy  = EXCLUDED.answer_relevancy,
                chunk_count       = EXCLUDED.chunk_count,
                collection        = EXCLUDED.collection,
                top_k             = EXCLUDED.top_k
            """;
        jdbc.update(sql,
            query.queryId(),
            tenantId != null ? tenantId : "default",
            query.spanId(), query.runId(), query.query(),
            query.retrievedChunks(), query.similarityScores(), query.latencyMs(),
            toJson(query.metadata()),
            query.contextPrecision(), query.contextRecall(),
            query.faithfulness(), query.answerRelevancy(),
            query.chunkCount(), query.collection(), query.topK()
        );
    }

    public void updateScores(
            @NonNull String queryId,
            @Nullable Double faithfulness,
            @Nullable Double answerRelevancy) {
        jdbc.update("""
            UPDATE rag_queries
            SET faithfulness = COALESCE(?, faithfulness),
                answer_relevancy = COALESCE(?, answer_relevancy)
            WHERE query_id = ?
            """, faithfulness, answerRelevancy, queryId);
    }

    public void saveQueryEmbedding(@NonNull String queryId, @NonNull float[] embedding) {
        // Store embedding as JSON array for VP-tree clustering
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        jdbc.update("UPDATE rag_queries SET query_embedding = ?::jsonb WHERE query_id = ?",
            sb.toString(), queryId);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public @NonNull List<RagQuery> findByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query(
                "SELECT * FROM rag_queries WHERE run_id = ? AND tenant_id = ? ORDER BY query_id ASC",
                rowMapper, runId, tenantId);
        }
        return jdbc.query(
            "SELECT * FROM rag_queries WHERE run_id = ? ORDER BY query_id ASC",
            rowMapper, runId);
    }

    public @NonNull Optional<RagQuery> findByQueryId(@NonNull String queryId) {
        String tenantId = tenantOrDefault();
        List<RagQuery> results = jdbc.query(
            "SELECT * FROM rag_queries WHERE query_id = ? AND tenant_id = ?",
            rowMapper, queryId, tenantId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public @NonNull List<RagQuery> listQueries(
            @NonNull Instant from, @NonNull Instant to,
            @Nullable String collection,
            int offset, int limit) {
        String tenantId = tenantOrDefault();
        if (collection != null) {
            return jdbc.query("""
                SELECT * FROM rag_queries
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ? AND collection = ?
                ORDER BY created_at DESC LIMIT ? OFFSET ?
                """, rowMapper, tenantId, from, to, collection, limit, offset);
        }
        return jdbc.query("""
            SELECT * FROM rag_queries
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
            ORDER BY created_at DESC LIMIT ? OFFSET ?
            """, rowMapper, tenantId, from, to, limit, offset);
    }

    public long countQueries(@NonNull Instant from, @NonNull Instant to, @Nullable String collection) {
        String tenantId = tenantOrDefault();
        if (collection != null) {
            return Objects.requireNonNullElse(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rag_queries WHERE tenant_id=? AND created_at>=? AND created_at<? AND collection=?",
                    Long.class, tenantId, from, to, collection), 0L);
        }
        return Objects.requireNonNullElse(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_queries WHERE tenant_id=? AND created_at>=? AND created_at<?",
                Long.class, tenantId, from, to), 0L);
    }

    // ── Aggregate analytics ───────────────────────────────────────────────────

    /**
     * Core metrics snapshot for the overview KPIs.
     */
    public @NonNull Map<String, Object> getAggregateMetrics(
            @NonNull Instant from, @NonNull Instant to,
            @Nullable String collection) {
        String tenantId = tenantOrDefault();
        String collFilter = collection != null ? "AND collection = ?" : "";
        String sql = """
            SELECT
                COUNT(*)                                                              AS query_count,
                COALESCE(AVG(latency_ms), 0)                                          AS avg_latency_ms,
                COALESCE(PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latency_ms), 0) AS p50_latency_ms,
                COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) AS p95_latency_ms,
                COALESCE(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms), 0) AS p99_latency_ms,
                COALESCE(AVG(context_precision),  0)                                  AS avg_context_precision,
                COALESCE(AVG(context_recall),     0)                                  AS avg_context_recall,
                COALESCE(AVG(faithfulness),       0)                                  AS avg_faithfulness,
                COALESCE(AVG(answer_relevancy),   0)                                  AS avg_answer_relevancy,
                COALESCE(
                    SUM(CASE WHEN metadata->>'cache_hit' = 'true' THEN 1 ELSE 0 END)::FLOAT
                    / NULLIF(COUNT(*), 0), 0)                                          AS hit_rate,
                COALESCE(AVG(chunk_count), 0)                                          AS avg_chunk_count
            FROM rag_queries
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
            """ + collFilter;
        Object[] args = collection != null
            ? new Object[]{tenantId, from, to, collection}
            : new Object[]{tenantId, from, to};

        return jdbc.queryForMap(sql, args);
    }

    /**
     * Latency histogram with fixed buckets.
     */
    public @NonNull List<Map<String, Object>> getLatencyHistogram(
            @NonNull Instant from, @NonNull Instant to,
            @Nullable String collection) {
        String tenantId = tenantOrDefault();
        String collFilter = collection != null ? "AND collection = ?" : "";
        String sql = """
            SELECT
                CASE
                    WHEN latency_ms < 50   THEN '<50ms'
                    WHEN latency_ms < 100  THEN '50–100ms'
                    WHEN latency_ms < 200  THEN '100–200ms'
                    WHEN latency_ms < 500  THEN '200–500ms'
                    ELSE '>500ms'
                END AS bucket,
                COUNT(*) AS count,
                MIN(latency_ms) AS min_ms
            FROM rag_queries
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ? """ + collFilter + """
            GROUP BY 1, min_ms
            ORDER BY min_ms
            """;
        Object[] args = collection != null
            ? new Object[]{tenantId, from, to, collection}
            : new Object[]{tenantId, from, to};
        return jdbc.queryForList(sql, args);
    }

    /**
     * Top N queries by frequency with average relevance score.
     */
    public @NonNull List<Map<String, Object>> getTopQueries(
            @NonNull Instant from, @NonNull Instant to,
            @Nullable String collection, int limit) {
        String tenantId = tenantOrDefault();
        String collFilter = collection != null ? "AND collection = ?" : "";
        String sql = """
            SELECT
                query_text                         AS query,
                COUNT(*)                           AS count,
                COALESCE(AVG(context_precision), 0) AS avg_score,
                COALESCE(AVG(faithfulness),      0) AS avg_faithfulness,
                COALESCE(AVG(latency_ms),        0) AS avg_latency_ms
            FROM rag_queries
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ? """ + collFilter + """
            GROUP BY query_text
            ORDER BY count DESC, avg_score DESC
            LIMIT ?
            """;
        Object[] args = collection != null
            ? new Object[]{tenantId, from, to, collection, limit}
            : new Object[]{tenantId, from, to, limit};
        return jdbc.queryForList(sql, args);
    }

    /**
     * Time-series trend for precision, recall, faithfulness, and latency.
     */
    public @NonNull List<Map<String, Object>> getTrend(
            @NonNull Instant from, @NonNull Instant to,
            @NonNull String granularity,
            @Nullable String collection) {
        String tenantId = tenantOrDefault();
        String trunc = switch (granularity) {
            case "hour"  -> "hour";
            case "day"   -> "day";
            case "week"  -> "week";
            default      -> "day";
        };
        String collFilter = collection != null ? "AND collection = ?" : "";
        String sql = String.format("""
            SELECT
                date_trunc('%s', created_at)         AS period,
                COUNT(*)                             AS query_count,
                COALESCE(AVG(context_precision),  0) AS avg_precision,
                COALESCE(AVG(context_recall),     0) AS avg_recall,
                COALESCE(AVG(faithfulness),       0) AS avg_faithfulness,
                COALESCE(AVG(answer_relevancy),   0) AS avg_answer_relevancy,
                COALESCE(AVG(latency_ms),         0) AS avg_latency_ms,
                COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) AS p95_latency_ms
            FROM rag_queries
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ? %s
            GROUP BY 1
            ORDER BY 1
            """, trunc, collFilter);
        Object[] args = collection != null
            ? new Object[]{tenantId, from, to, collection}
            : new Object[]{tenantId, from, to};
        return jdbc.queryForList(sql, args);
    }

    /**
     * Per-collection breakdown for the multi-collection view.
     */
    public @NonNull List<Map<String, Object>> getCollectionBreakdown(
            @NonNull Instant from, @NonNull Instant to) {
        String tenantId = tenantOrDefault();
        return jdbc.queryForList("""
            SELECT
                COALESCE(collection, 'unknown')       AS collection,
                COUNT(*)                              AS query_count,
                COALESCE(AVG(latency_ms),         0)  AS avg_latency_ms,
                COALESCE(AVG(context_precision),  0)  AS avg_precision,
                COALESCE(AVG(faithfulness),       0)  AS avg_faithfulness
            FROM rag_queries
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
            GROUP BY 1
            ORDER BY query_count DESC
            """, tenantId, from, to);
    }

    /**
     * Distinct collections seen in the given window.
     */
    public @NonNull List<String> getCollections(@NonNull Instant from, @NonNull Instant to) {
        String tenantId = tenantOrDefault();
        return jdbc.queryForList("""
            SELECT DISTINCT collection FROM rag_queries
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ? AND collection IS NOT NULL
            ORDER BY collection
            """, String.class, tenantId, from, to);
    }

    /**
     * Embedding drift snapshots for the drift alert strip.
     */
    public @NonNull List<Map<String, Object>> getDriftSnapshots(
            @NonNull Instant from, @NonNull Instant to,
            @Nullable String collection) {
        String tenantId = tenantOrDefault();
        String collFilter = collection != null ? "AND collection = ?" : "";
        String sql = """
            SELECT snapshot_id, collection, period_start, period_end,
                   mean_cosine_shift, query_volume_delta, precision_delta, alert_level
            FROM rag_drift_snapshots
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ? """ + collFilter + """
            ORDER BY created_at DESC
            LIMIT 60
            """;
        Object[] args = collection != null
            ? new Object[]{tenantId, from, to, collection}
            : new Object[]{tenantId, from, to};
        return jdbc.queryForList(sql, args);
    }

    /**
     * Queries without embeddings (up to {@code limit}) for async embedding batch.
     */
    public @NonNull List<Map<String, Object>> findQueriesNeedingEmbedding(
            @NonNull Instant from, @NonNull Instant to, int limit) {
        String tenantId = tenantOrDefault();
        return jdbc.queryForList("""
            SELECT query_id, query_text FROM rag_queries
            WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
              AND query_embedding IS NULL
            ORDER BY created_at DESC
            LIMIT ?
            """, tenantId, from, to, limit);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String tenantOrDefault() {
        String t = TenantContext.getTenantIdOrNull();
        return t != null ? t : "default";
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
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    nullableDouble(rs, "context_precision"),
                    nullableDouble(rs, "context_recall"),
                    nullableDouble(rs, "faithfulness"),
                    nullableDouble(rs, "answer_relevancy"),
                    safeInt(rs, "chunk_count"),
                    rs.getString("collection"),
                    safeInt(rs, "top_k")
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }

        private static Double nullableDouble(ResultSet rs, String col) throws SQLException {
            double v = rs.getDouble(col);
            return rs.wasNull() ? null : v;
        }

        private static int safeInt(ResultSet rs, String col) throws SQLException {
            try {
                return rs.getInt(col);
            } catch (SQLException e) {
                return 0;
            }
        }
    }
}
