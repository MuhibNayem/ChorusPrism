package com.chorus.observe.persistence;

import com.chorus.observe.model.RetentionPolicy;
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

public class RetentionPolicyRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<RetentionPolicy> rowMapper = new RetentionPolicyRowMapper();

    public RetentionPolicyRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull RetentionPolicy policy) {
        String sql = """
            INSERT INTO retention_policies (policy_id, tenant_id, name, resource_type, retention_days, archive_enabled, archive_location, enabled, last_run_at, last_run_deleted, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (policy_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                resource_type = EXCLUDED.resource_type,
                retention_days = EXCLUDED.retention_days,
                archive_enabled = EXCLUDED.archive_enabled,
                archive_location = EXCLUDED.archive_location,
                enabled = EXCLUDED.enabled,
                last_run_at = EXCLUDED.last_run_at,
                last_run_deleted = EXCLUDED.last_run_deleted,
                updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            policy.policyId(), policy.tenantId(), policy.name(), policy.resourceType(),
            policy.retentionDays(), policy.archiveEnabled(), policy.archiveLocation(),
            policy.enabled(),
            policy.lastRunAt() != null ? Timestamp.from(policy.lastRunAt()) : null,
            policy.lastRunDeleted(),
            Timestamp.from(policy.createdAt()), Timestamp.from(policy.updatedAt()));
    }

    public @NonNull Optional<RetentionPolicy> findById(@NonNull String policyId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM retention_policies WHERE policy_id = ?", rowMapper, policyId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<RetentionPolicy> findByTenant(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM retention_policies WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public @NonNull List<RetentionPolicy> findEnabledByTenant(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM retention_policies WHERE tenant_id = ? AND enabled = TRUE ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public void deleteById(@NonNull String policyId) {
        jdbc.update("DELETE FROM retention_policies WHERE policy_id = ?", policyId);
    }

    private static final class RetentionPolicyRowMapper implements RowMapper<RetentionPolicy> {
        @Override
        public RetentionPolicy mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RetentionPolicy(
                rs.getString("policy_id"),
                rs.getString("tenant_id"),
                rs.getString("name"),
                rs.getString("resource_type"),
                rs.getInt("retention_days"),
                rs.getBoolean("archive_enabled"),
                rs.getString("archive_location"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("last_run_at") != null ? rs.getTimestamp("last_run_at").toInstant() : null,
                rs.getLong("last_run_deleted"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
