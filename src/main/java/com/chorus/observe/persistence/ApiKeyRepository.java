package com.chorus.observe.persistence;

import com.chorus.observe.model.ApiKey;
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
import java.util.Objects;
import java.util.Optional;

public class ApiKeyRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<ApiKey> rowMapper;

    public ApiKeyRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new ApiKeyRowMapper(mapper);
    }

    public void save(@NonNull ApiKey apiKey) {
        String sql = """
            INSERT INTO api_keys (key_hash, tenant_id, user_id, name, scopes, expires_at, last_used_at, created_at, revoked_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (key_hash) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                user_id = EXCLUDED.user_id,
                name = EXCLUDED.name,
                scopes = EXCLUDED.scopes,
                expires_at = EXCLUDED.expires_at,
                last_used_at = EXCLUDED.last_used_at,
                revoked_at = EXCLUDED.revoked_at
            """;
        try {
            jdbc.update(sql,
                apiKey.keyHash(), apiKey.tenantId(), apiKey.userId(), apiKey.name(),
                mapper.writeValueAsString(apiKey.scopes()),
                apiKey.expiresAt() != null ? Timestamp.from(apiKey.expiresAt()) : null,
                apiKey.lastUsedAt() != null ? Timestamp.from(apiKey.lastUsedAt()) : null,
                Timestamp.from(apiKey.createdAt()),
                apiKey.revokedAt() != null ? Timestamp.from(apiKey.revokedAt()) : null);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize scopes", e);
        }
    }

    public @NonNull Optional<ApiKey> findByKeyHash(@NonNull String keyHash) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM api_keys WHERE key_hash = ?", rowMapper, keyHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<ApiKey> findByTenant(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM api_keys WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public void updateLastUsed(@NonNull String keyHash, @NonNull Instant lastUsedAt) {
        jdbc.update("UPDATE api_keys SET last_used_at = ? WHERE key_hash = ?",
            Timestamp.from(lastUsedAt), keyHash);
    }

    public void revoke(@NonNull String keyHash, @NonNull Instant revokedAt) {
        jdbc.update("UPDATE api_keys SET revoked_at = ? WHERE key_hash = ?",
            Timestamp.from(revokedAt), keyHash);
    }

    private static final class ApiKeyRowMapper implements RowMapper<ApiKey> {
        private final ObjectMapper mapper;

        ApiKeyRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public ApiKey mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new ApiKey(
                    rs.getString("key_hash"),
                    rs.getString("tenant_id"),
                    rs.getString("user_id"),
                    rs.getString("name"),
                    mapper.readValue(rs.getString("scopes"), new TypeReference<List<String>>() {}),
                    rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toInstant() : null,
                    rs.getTimestamp("last_used_at") != null ? rs.getTimestamp("last_used_at").toInstant() : null,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant() : null
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
