package com.chorus.observe.persistence;

import com.chorus.observe.model.ScimToken;
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
import java.util.UUID;

public class ScimTokenRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<ScimToken> rowMapper;

    public ScimTokenRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new ScimTokenRowMapper(mapper);
    }

    public void save(@NonNull ScimToken token) {
        String sql = """
            INSERT INTO scim_tokens (id, tenant_id, name, token_hash, scopes, created_at, expires_at, revoked_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (token_hash) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                scopes = EXCLUDED.scopes,
                expires_at = EXCLUDED.expires_at,
                revoked_at = EXCLUDED.revoked_at
            """;
        try {
            jdbc.update(sql,
                token.id() != null ? token.id() : UUID.randomUUID(),
                token.tenantId(), token.name(), token.tokenHash(),
                mapper.writeValueAsString(token.scopes()),
                Timestamp.from(token.createdAt()),
                token.expiresAt() != null ? Timestamp.from(token.expiresAt()) : null,
                token.revokedAt() != null ? Timestamp.from(token.revokedAt()) : null);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize scopes", e);
        }
    }

    public @NonNull Optional<ScimToken> findById(@NonNull UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM scim_tokens WHERE id = ?", rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull Optional<ScimToken> findByTokenHash(@NonNull String tokenHash) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM scim_tokens WHERE token_hash = ?", rowMapper, tokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<ScimToken> findByTenantId(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM scim_tokens WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public void revokeById(@NonNull UUID id, @NonNull Instant revokedAt) {
        jdbc.update("UPDATE scim_tokens SET revoked_at = ? WHERE id = ?",
            Timestamp.from(revokedAt), id);
    }

    public void deleteById(@NonNull UUID id) {
        jdbc.update("DELETE FROM scim_tokens WHERE id = ?", id);
    }

    private static final class ScimTokenRowMapper implements RowMapper<ScimToken> {
        private final ObjectMapper mapper;

        ScimTokenRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public ScimToken mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new ScimToken(
                    rs.getObject("id", UUID.class),
                    rs.getString("tenant_id"),
                    rs.getString("name"),
                    rs.getString("token_hash"),
                    mapper.readValue(rs.getString("scopes"), new TypeReference<List<String>>() {}),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toInstant() : null,
                    rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant() : null
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
