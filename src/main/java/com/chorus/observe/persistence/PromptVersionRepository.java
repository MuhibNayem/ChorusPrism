package com.chorus.observe.persistence;

import com.chorus.observe.model.PromptVersion;
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

/**
 * JDBC repository for prompt versions.
 */
public class PromptVersionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<PromptVersion> rowMapper;

    public PromptVersionRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new PromptVersionRowMapper(mapper);
    }

    public void save(@NonNull PromptVersion prompt) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO prompt_versions (version_id, tenant_id, name, content, model, temperature, max_tokens, metadata, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (version_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                content = EXCLUDED.content,
                model = EXCLUDED.model,
                temperature = EXCLUDED.temperature,
                max_tokens = EXCLUDED.max_tokens,
                metadata = EXCLUDED.metadata,
                created_by = EXCLUDED.created_by
            """;
        jdbc.update(sql,
            prompt.versionId(), tenantId != null ? tenantId : "default", prompt.name(), prompt.content(), prompt.model(),
            prompt.temperature(), prompt.maxTokens(), toJson(prompt.metadata()),
            prompt.createdBy(), Timestamp.from(prompt.createdAt())
        );
    }

    public @NonNull Optional<PromptVersion> findById(@NonNull String versionId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM prompt_versions WHERE version_id = ? AND tenant_id = ?", rowMapper, versionId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM prompt_versions WHERE version_id = ?", rowMapper, versionId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<PromptVersion> findByName(@NonNull String name) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM prompt_versions WHERE name = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, name, tenantId);
        }
        return jdbc.query("SELECT * FROM prompt_versions WHERE name = ? ORDER BY created_at DESC", rowMapper, name);
    }

    public @NonNull List<PromptVersion> findByName(@NonNull String name, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM prompt_versions WHERE name = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, name, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM prompt_versions WHERE name = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, name, limit, offset);
    }

    public long countByName(@NonNull String name) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE name = ? AND tenant_id = ?", Long.class, name, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE name = ?", Long.class, name);
        return count != null ? count : 0L;
    }

    public @NonNull List<PromptVersion> findAll() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM prompt_versions WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
        }
        return jdbc.query("SELECT * FROM prompt_versions ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<PromptVersion> findAll(int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM prompt_versions WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM prompt_versions ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE tenant_id = ?", Long.class, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions", Long.class);
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String versionId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM prompt_versions WHERE version_id = ? AND tenant_id = ?", versionId, tenantId);
        } else {
            jdbc.update("DELETE FROM prompt_versions WHERE version_id = ?", versionId);
        }
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class PromptVersionRowMapper implements RowMapper<PromptVersion> {
        private final ObjectMapper mapper;

        PromptVersionRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public PromptVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new PromptVersion(
                    rs.getString("version_id"),
                    rs.getString("name"),
                    rs.getString("content"),
                    rs.getString("model"),
                    rs.getObject("temperature", Double.class),
                    rs.getObject("max_tokens", Integer.class),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getString("created_by"),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
