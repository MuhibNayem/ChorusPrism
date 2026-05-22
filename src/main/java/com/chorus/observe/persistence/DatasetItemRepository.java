package com.chorus.observe.persistence;

import com.chorus.observe.model.DatasetItem;
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

/**
 * JDBC repository for dataset items.
 */
public class DatasetItemRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<DatasetItem> rowMapper;

    public DatasetItemRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new DatasetItemRowMapper(mapper);
    }

    public void save(@NonNull DatasetItem item) {
        String sql = """
            INSERT INTO dataset_items (item_id, dataset_id, input, expected_output, metadata, tags, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (item_id) DO UPDATE SET
                dataset_id = EXCLUDED.dataset_id,
                input = EXCLUDED.input,
                expected_output = EXCLUDED.expected_output,
                metadata = EXCLUDED.metadata,
                tags = EXCLUDED.tags
            """;
        jdbc.update(sql,
            item.itemId(), item.datasetId(), item.input(), item.expectedOutput(),
            toJson(item.metadata()), toJson(item.tags()),
            Timestamp.from(item.createdAt())
        );
    }

    public @NonNull Optional<DatasetItem> findById(@NonNull String itemId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM dataset_items WHERE item_id = ?", rowMapper, itemId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<DatasetItem> findByDatasetId(@NonNull String datasetId) {
        return jdbc.query("SELECT * FROM dataset_items WHERE dataset_id = ? ORDER BY created_at", rowMapper, datasetId);
    }

    public @NonNull List<DatasetItem> findByDatasetId(@NonNull String datasetId, int limit, int offset) {
        return jdbc.query("SELECT * FROM dataset_items WHERE dataset_id = ? ORDER BY created_at LIMIT ? OFFSET ?", rowMapper, datasetId, limit, offset);
    }

    public void deleteById(@NonNull String itemId) {
        jdbc.update("DELETE FROM dataset_items WHERE item_id = ?", itemId);
    }

    public void deleteByDatasetId(@NonNull String datasetId) {
        jdbc.update("DELETE FROM dataset_items WHERE dataset_id = ?", datasetId);
    }

    public long countByDatasetId(@NonNull String datasetId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM dataset_items WHERE dataset_id = ?", Long.class, datasetId);
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class DatasetItemRowMapper implements RowMapper<DatasetItem> {
        private final ObjectMapper mapper;

        DatasetItemRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public DatasetItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new DatasetItem(
                    rs.getString("item_id"),
                    rs.getString("dataset_id"),
                    rs.getString("input"),
                    rs.getString("expected_output"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    mapper.readValue(rs.getString("tags"), new TypeReference<Map<String, String>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
