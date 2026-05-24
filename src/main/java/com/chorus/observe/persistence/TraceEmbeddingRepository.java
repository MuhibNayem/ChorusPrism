package com.chorus.observe.persistence;

import com.chorus.observe.model.TraceEmbedding;
import com.chorus.observe.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TraceEmbeddingRepository {

    private static final Logger LOG = LoggerFactory.getLogger(TraceEmbeddingRepository.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<TraceEmbedding> rowMapper;
    private boolean nativeVectorSupported = false;

    public TraceEmbeddingRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new EmbeddingRowMapper(mapper);
    }

    @PostConstruct
    public void init() {
        if (jdbc == null) return;
        try {
            String checkSql = """
                SELECT COUNT(*) 
                FROM information_schema.columns 
                WHERE table_name = 'trace_embeddings' AND column_name = 'vector_native'
                """;
            Integer count = jdbc.queryForObject(checkSql, Integer.class);
            this.nativeVectorSupported = count != null && count > 0;
            LOG.info("Native pgvector support detected in database: {}", nativeVectorSupported);
        } catch (Exception e) {
            LOG.warn("Failed to check dynamic pgvector availability. Falling back to JSONB storage. Reason: {}", e.getMessage());
            this.nativeVectorSupported = false;
        }
    }

    private @NonNull String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public void save(@NonNull TraceEmbedding embedding) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql;
        if (nativeVectorSupported) {
            sql = """
                INSERT INTO trace_embeddings (embedding_id, tenant_id, run_id, span_id, model, vector, vector_native, text_source, metadata, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::vector, ?, ?::jsonb, ?)
                ON CONFLICT (embedding_id) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    vector = EXCLUDED.vector,
                    vector_native = EXCLUDED.vector_native,
                    text_source = EXCLUDED.text_source,
                    metadata = EXCLUDED.metadata
                """;
            jdbc.update(sql,
                embedding.embeddingId(), tenantId != null ? tenantId : "default", embedding.runId(), embedding.spanId(), embedding.model(),
                toJson(embedding.vector()), toVectorString(embedding.vector()), embedding.textSource(), toJson(embedding.metadata()),
                Timestamp.from(embedding.createdAt()));
        } else {
            sql = """
                INSERT INTO trace_embeddings (embedding_id, tenant_id, run_id, span_id, model, vector, text_source, metadata, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?)
                ON CONFLICT (embedding_id) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    vector = EXCLUDED.vector,
                    text_source = EXCLUDED.text_source,
                    metadata = EXCLUDED.metadata
                """;
            jdbc.update(sql,
                embedding.embeddingId(), tenantId != null ? tenantId : "default", embedding.runId(), embedding.spanId(), embedding.model(),
                toJson(embedding.vector()), embedding.textSource(), toJson(embedding.metadata()),
                Timestamp.from(embedding.createdAt()));
        }
    }

    public @NonNull Optional<TraceEmbedding> findById(@NonNull String embeddingId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM trace_embeddings WHERE embedding_id = ? AND tenant_id = ?", rowMapper, embeddingId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM trace_embeddings WHERE embedding_id = ?", rowMapper, embeddingId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<TraceEmbedding> findByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM trace_embeddings WHERE run_id = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, runId, tenantId);
        }
        return jdbc.query("SELECT * FROM trace_embeddings WHERE run_id = ? ORDER BY created_at DESC", rowMapper, runId);
    }

    public @NonNull List<TraceEmbedding> findByModel(@NonNull String model) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM trace_embeddings WHERE model = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, model, tenantId);
        }
        return jdbc.query("SELECT * FROM trace_embeddings WHERE model = ? ORDER BY created_at DESC", rowMapper, model);
    }

    public @NonNull List<TraceEmbedding> findByModel(@NonNull String model, int limit) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM trace_embeddings WHERE model = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ?", rowMapper, model, tenantId, limit);
        }
        return jdbc.query("SELECT * FROM trace_embeddings WHERE model = ? ORDER BY created_at DESC LIMIT ?", rowMapper, model, limit);
    }

    public @NonNull List<TraceEmbedding> findAll() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM trace_embeddings WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 10000", rowMapper, tenantId);
        }
        return jdbc.query("SELECT * FROM trace_embeddings ORDER BY created_at DESC LIMIT 10000", rowMapper);
    }

    /**
     * Find nearest neighbors using pgvector cosine distance if supported, falling back to H2-compatible findAll.
     */
    public @NonNull List<TraceEmbedding> findNearestNeighbors(@NonNull float[] queryVector, int limit) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (nativeVectorSupported) {
            String vectorStr = toVectorString(queryVector);
            if (tenantId != null) {
                return jdbc.query("SELECT * FROM trace_embeddings WHERE tenant_id = ? ORDER BY vector_native <=> ?::vector LIMIT ?", rowMapper, tenantId, vectorStr, limit);
            }
            return jdbc.query("SELECT * FROM trace_embeddings ORDER BY vector_native <=> ?::vector LIMIT ?", rowMapper, vectorStr, limit);
        }
        // Fallback: return top elements from database for in-memory processing
        return findAll();
    }

    public void deleteByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM trace_embeddings WHERE run_id = ? AND tenant_id = ?", runId, tenantId);
        } else {
            jdbc.update("DELETE FROM trace_embeddings WHERE run_id = ?", runId);
        }
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class EmbeddingRowMapper implements RowMapper<TraceEmbedding> {
        private final ObjectMapper mapper;

        EmbeddingRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public TraceEmbedding mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                List<Float> vectorList = mapper.readValue(rs.getString("vector"), new TypeReference<List<Float>>() {});
                float[] vector = new float[vectorList.size()];
                for (int i = 0; i < vectorList.size(); i++) {
                    vector[i] = vectorList.get(i);
                }
                return new TraceEmbedding(
                    rs.getString("embedding_id"),
                    rs.getString("run_id"),
                    rs.getString("span_id"),
                    rs.getString("model"),
                    vector,
                    rs.getString("text_source"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
