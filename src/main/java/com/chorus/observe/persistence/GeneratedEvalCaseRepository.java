package com.chorus.observe.persistence;

import com.chorus.observe.model.GeneratedEvalCase;
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
 * JDBC repository for generated eval cases.
 */
public class GeneratedEvalCaseRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<GeneratedEvalCase> rowMapper;

    public GeneratedEvalCaseRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new GeneratedEvalCaseRowMapper(mapper);
    }

    public void save(@NonNull GeneratedEvalCase evalCase) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO generated_eval_cases (case_id, tenant_id, source_run_id, source_span_id, input, expected_output, metadata, status, reviewed_by, reviewed_at, review_notes, dataset_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (case_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                source_run_id = EXCLUDED.source_run_id,
                source_span_id = EXCLUDED.source_span_id,
                input = EXCLUDED.input,
                expected_output = EXCLUDED.expected_output,
                metadata = EXCLUDED.metadata,
                status = EXCLUDED.status,
                reviewed_by = EXCLUDED.reviewed_by,
                reviewed_at = EXCLUDED.reviewed_at,
                review_notes = EXCLUDED.review_notes,
                dataset_id = EXCLUDED.dataset_id
            """;
        jdbc.update(sql,
            evalCase.caseId(), tenantId != null ? tenantId : "default", evalCase.sourceRunId(), evalCase.sourceSpanId(),
            evalCase.input(), evalCase.expectedOutput(), toJson(evalCase.metadata()),
            evalCase.status().name(), evalCase.reviewedBy(),
            evalCase.reviewedAt() != null ? Timestamp.from(evalCase.reviewedAt()) : null,
            evalCase.reviewNotes(), evalCase.datasetId(),
            Timestamp.from(evalCase.createdAt())
        );
    }

    public @NonNull Optional<GeneratedEvalCase> findById(@NonNull String caseId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM generated_eval_cases WHERE case_id = ? AND tenant_id = ?", rowMapper, caseId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM generated_eval_cases WHERE case_id = ?", rowMapper, caseId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<GeneratedEvalCase> findByStatus(GeneratedEvalCase.@NonNull Status status) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM generated_eval_cases WHERE status = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, status.name(), tenantId);
        }
        return jdbc.query("SELECT * FROM generated_eval_cases WHERE status = ? ORDER BY created_at DESC", rowMapper, status.name());
    }

    public @NonNull List<GeneratedEvalCase> findByStatus(GeneratedEvalCase.@NonNull Status status, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM generated_eval_cases WHERE status = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, status.name(), tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM generated_eval_cases WHERE status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, status.name(), limit, offset);
    }

    public long countByStatus(GeneratedEvalCase.@NonNull Status status) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM generated_eval_cases WHERE status = ? AND tenant_id = ?", Long.class, status.name(), tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM generated_eval_cases WHERE status = ?", Long.class, status.name());
        return count != null ? count : 0L;
    }

    public @NonNull List<GeneratedEvalCase> findBySourceRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM generated_eval_cases WHERE source_run_id = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, runId, tenantId);
        }
        return jdbc.query("SELECT * FROM generated_eval_cases WHERE source_run_id = ? ORDER BY created_at DESC", rowMapper, runId);
    }

    public @NonNull List<GeneratedEvalCase> findAll() {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM generated_eval_cases WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
        }
        return jdbc.query("SELECT * FROM generated_eval_cases ORDER BY created_at DESC", rowMapper);
    }

    public void deleteById(@NonNull String caseId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM generated_eval_cases WHERE case_id = ? AND tenant_id = ?", caseId, tenantId);
        } else {
            jdbc.update("DELETE FROM generated_eval_cases WHERE case_id = ?", caseId);
        }
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class GeneratedEvalCaseRowMapper implements RowMapper<GeneratedEvalCase> {
        private final ObjectMapper mapper;

        GeneratedEvalCaseRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public GeneratedEvalCase mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new GeneratedEvalCase(
                    rs.getString("case_id"),
                    rs.getString("source_run_id"),
                    rs.getString("source_span_id"),
                    rs.getString("input"),
                    rs.getString("expected_output"),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    GeneratedEvalCase.Status.valueOf(rs.getString("status")),
                    rs.getString("reviewed_by"),
                    rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toInstant() : null,
                    rs.getString("review_notes"),
                    rs.getString("dataset_id"),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
