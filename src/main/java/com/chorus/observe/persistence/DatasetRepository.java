package com.chorus.observe.persistence;

import com.chorus.observe.model.Dataset;
import com.chorus.observe.security.TenantContext;
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

public class DatasetRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Dataset> rowMapper;

    public DatasetRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new DatasetRowMapper(mapper);
    }

    public void save(@NonNull Dataset dataset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO datasets (dataset_id, tenant_id, name, description, tags, source, split_config, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?)
            ON CONFLICT (dataset_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                tags = EXCLUDED.tags,
                source = EXCLUDED.source,
                split_config = EXCLUDED.split_config,
                updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            dataset.datasetId(), tenantId != null ? tenantId : "default", dataset.name(), dataset.description(),
            toJson(dataset.tags()), dataset.source(), toJson(dataset.splitConfig()),
            Timestamp.from(dataset.createdAt()), Timestamp.from(dataset.updatedAt())
        );
    }

    public @NonNull Optional<Dataset> findById(@NonNull String datasetId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM datasets WHERE dataset_id = ? AND tenant_id = ?", rowMapper, datasetId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM datasets WHERE dataset_id = ?", rowMapper, datasetId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Dataset> findAll() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM datasets WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
        }
        return jdbc.query("SELECT * FROM datasets ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<Dataset> findAll(int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM datasets WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM datasets ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM datasets WHERE tenant_id = ?", Long.class, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM datasets", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<Dataset> findBySource(@NonNull String source) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM datasets WHERE source = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, source, tenantId);
        }
        return jdbc.query("SELECT * FROM datasets WHERE source = ? ORDER BY created_at DESC", rowMapper, source);
    }

    public @NonNull List<Dataset> findBySource(@NonNull String source, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM datasets WHERE source = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, source, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM datasets WHERE source = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, source, limit, offset);
    }

    public long countBySource(@NonNull String source) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM datasets WHERE source = ? AND tenant_id = ?", Long.class, source, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM datasets WHERE source = ?", Long.class, source);
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String datasetId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM datasets WHERE dataset_id = ? AND tenant_id = ?", datasetId, tenantId);
        } else {
            jdbc.update("DELETE FROM datasets WHERE dataset_id = ?", datasetId);
        }
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class DatasetRowMapper implements RowMapper<Dataset> {
        private final ObjectMapper mapper;

        DatasetRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Dataset mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Dataset(
                    rs.getString("dataset_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    mapper.readValue(rs.getString("tags"), new TypeReference<Map<String, String>>() {}),
                    rs.getString("source"),
                    mapper.readValue(rs.getString("split_config"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
