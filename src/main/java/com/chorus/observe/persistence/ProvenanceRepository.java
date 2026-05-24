package com.chorus.observe.persistence;

import com.chorus.observe.model.ProvenanceEntry;
import com.chorus.observe.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC repository for provenance entries (causal DAG nodes).
 */
public class ProvenanceRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<ProvenanceEntry> rowMapper;

    public ProvenanceRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new ProvenanceRowMapper(mapper);
    }

    public void save(@NonNull ProvenanceEntry entry) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO provenance_entries (entry_id, tenant_id, run_id, agent_id, decision_type, input_state, reasoning, output, parent_ids, timestamp, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb)
            ON CONFLICT (entry_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                run_id = EXCLUDED.run_id,
                agent_id = EXCLUDED.agent_id,
                decision_type = EXCLUDED.decision_type,
                input_state = EXCLUDED.input_state,
                reasoning = EXCLUDED.reasoning,
                output = EXCLUDED.output,
                parent_ids = EXCLUDED.parent_ids,
                timestamp = EXCLUDED.timestamp,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            entry.entryId(), tenantId != null ? tenantId : "default", entry.runId(), entry.agentId(), entry.decisionType(),
            entry.inputState(), entry.reasoning(), entry.output(),
            toJson(entry.parentIds()), Timestamp.from(entry.timestamp()),
            toJson(entry.metadata())
        );
    }

    public @NonNull List<ProvenanceEntry> findByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query(
                "SELECT * FROM provenance_entries WHERE run_id = ? AND tenant_id = ? ORDER BY timestamp ASC",
                rowMapper, runId, tenantId);
        }
        return jdbc.query(
            "SELECT * FROM provenance_entries WHERE run_id = ? ORDER BY timestamp ASC",
            rowMapper, runId);
    }

    public @NonNull List<ProvenanceEntry> findByRunId(@NonNull String runId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query(
                "SELECT * FROM provenance_entries WHERE run_id = ? AND tenant_id = ? ORDER BY timestamp ASC LIMIT ? OFFSET ?",
                rowMapper, runId, tenantId, limit, offset);
        }
        return jdbc.query(
            "SELECT * FROM provenance_entries WHERE run_id = ? ORDER BY timestamp ASC LIMIT ? OFFSET ?",
            rowMapper, runId, limit, offset);
    }

    public long countByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM provenance_entries WHERE run_id = ? AND tenant_id = ?", Long.class, runId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM provenance_entries WHERE run_id = ?", Long.class, runId);
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class ProvenanceRowMapper implements RowMapper<ProvenanceEntry> {
        private final ObjectMapper mapper;

        ProvenanceRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public ProvenanceEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new ProvenanceEntry(
                    rs.getString("entry_id"),
                    rs.getString("run_id"),
                    rs.getString("agent_id"),
                    rs.getString("decision_type"),
                    rs.getString("input_state"),
                    rs.getString("reasoning"),
                    rs.getString("output"),
                    mapper.readValue(rs.getString("parent_ids"), new TypeReference<List<String>>() {}),
                    rs.getTimestamp("timestamp").toInstant(),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {})
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
