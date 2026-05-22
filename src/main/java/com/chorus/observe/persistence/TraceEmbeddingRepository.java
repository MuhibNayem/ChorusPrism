package com.chorus.observe.persistence;

import com.chorus.observe.model.TraceEmbedding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
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

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<TraceEmbedding> rowMapper;

    public TraceEmbeddingRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new EmbeddingRowMapper(mapper);
    }

    public void save(@NonNull TraceEmbedding embedding) {
        String sql = """
            INSERT INTO trace_embeddings (embedding_id, run_id, span_id, model, vector, text_source, metadata, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?)
            ON CONFLICT (embedding_id) DO UPDATE SET
                vector = EXCLUDED.vector,
                text_source = EXCLUDED.text_source,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            embedding.embeddingId(), embedding.runId(), embedding.spanId(), embedding.model(),
            toJson(embedding.vector()), embedding.textSource(), toJson(embedding.metadata()),
            Timestamp.from(embedding.createdAt()));
    }

    public @NonNull Optional<TraceEmbedding> findById(@NonNull String embeddingId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM trace_embeddings WHERE embedding_id = ?", rowMapper, embeddingId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<TraceEmbedding> findByRunId(@NonNull String runId) {
        return jdbc.query("SELECT * FROM trace_embeddings WHERE run_id = ? ORDER BY created_at DESC", rowMapper, runId);
    }

    public @NonNull List<TraceEmbedding> findByModel(@NonNull String model) {
        return jdbc.query("SELECT * FROM trace_embeddings WHERE model = ? ORDER BY created_at DESC", rowMapper, model);
    }

    public @NonNull List<TraceEmbedding> findByModel(@NonNull String model, int limit) {
        return jdbc.query("SELECT * FROM trace_embeddings WHERE model = ? ORDER BY created_at DESC LIMIT ?", rowMapper, model, limit);
    }

    public @NonNull List<TraceEmbedding> findAll() {
        return jdbc.query("SELECT * FROM trace_embeddings ORDER BY created_at DESC LIMIT 10000", rowMapper);
    }

    public void deleteByRunId(@NonNull String runId) {
        jdbc.update("DELETE FROM trace_embeddings WHERE run_id = ?", runId);
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
