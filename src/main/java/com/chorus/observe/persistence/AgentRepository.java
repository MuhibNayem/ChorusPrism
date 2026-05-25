package com.chorus.observe.persistence;

import com.chorus.observe.model.Agent;
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
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository for agents.
 */
public class AgentRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Agent> rowMapper;

    public AgentRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new AgentRowMapper(mapper);
    }

    public void save(@NonNull Agent agent) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO agents (agent_id, tenant_id, name, description, framework, runtime, owner, owner_email, tags, version, deployed_at, deployed_by, status, health, repo, branch, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (agent_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                framework = EXCLUDED.framework,
                runtime = EXCLUDED.runtime,
                owner = EXCLUDED.owner,
                owner_email = EXCLUDED.owner_email,
                tags = EXCLUDED.tags,
                version = EXCLUDED.version,
                deployed_at = EXCLUDED.deployed_at,
                deployed_by = EXCLUDED.deployed_by,
                status = EXCLUDED.status,
                health = EXCLUDED.health,
                repo = EXCLUDED.repo,
                branch = EXCLUDED.branch,
                updated_at = NOW()
            """;
        jdbc.update(sql,
            agent.agentId(), tenantId != null ? tenantId : "default", agent.name(), agent.description(),
            agent.framework(), agent.runtime(), agent.owner(),
            agent.ownerEmail(), toJson(agent.tags()),
            agent.version(),
            agent.deployedAt() != null ? Timestamp.from(agent.deployedAt()) : null,
            agent.deployedBy(),
            agent.status().name(),
            agent.health(),
            agent.repo(), agent.branch()
        );
    }

    public @NonNull Optional<Agent> findById(@NonNull String agentId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM agents WHERE agent_id = ? AND tenant_id = ?", rowMapper, agentId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM agents WHERE agent_id = ?", rowMapper, agentId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Agent> findAll() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM agents WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
        }
        return jdbc.query("SELECT * FROM agents ORDER BY created_at DESC", rowMapper);
    }

    public boolean exists(@NonNull String agentId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM agents WHERE agent_id = ? AND tenant_id = ?", Long.class, agentId, tenantId);
            return count != null && count > 0;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM agents WHERE agent_id = ?", Long.class, agentId);
        return count != null && count > 0;
    }

    public void deleteById(@NonNull String agentId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM agents WHERE agent_id = ? AND tenant_id = ?", agentId, tenantId);
        } else {
            jdbc.update("DELETE FROM agents WHERE agent_id = ?", agentId);
        }
    }

    public void upsertFromRun(@NonNull String agentId, @NonNull String framework) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO agents (agent_id, tenant_id, name, framework, status, tags, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'healthy', '["auto-provisioned"]'::jsonb, NOW(), NOW())
            ON CONFLICT (agent_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                framework = EXCLUDED.framework,
                updated_at = NOW()
            """;
        jdbc.update(sql, agentId, tenantId != null ? tenantId : "default", agentId, framework);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class AgentRowMapper implements RowMapper<Agent> {
        private final ObjectMapper mapper;

        AgentRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Agent mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Agent(
                    rs.getString("agent_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("framework"),
                    rs.getString("runtime"),
                    rs.getString("owner"),
                    rs.getString("owner_email"),
                    mapper.readValue(rs.getString("tags"), new TypeReference<List<String>>() {}),
                    rs.getString("version"),
                    rs.getTimestamp("deployed_at") != null ? rs.getTimestamp("deployed_at").toInstant() : null,
                    rs.getString("deployed_by"),
                    Agent.Status.valueOf(rs.getString("status")),
                    rs.getObject("health") != null ? rs.getDouble("health") : null,
                    rs.getString("repo"),
                    rs.getString("branch")
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
