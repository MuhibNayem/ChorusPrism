package com.chorus.observe.retention;

import com.chorus.observe.model.RetentionPolicy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Background scheduler that enforces retention policies by deleting old data.
 * <p>
 * Runs daily at 2 AM. For each enabled policy, deletes rows older than
 * {@code retentionDays} from the target resource table.
 */
public class DataRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DataRetentionScheduler.class);

    private final RetentionPolicyService retentionPolicyService;
    private final JdbcTemplate jdbc;

    public DataRetentionScheduler(@NonNull RetentionPolicyService retentionPolicyService, @NonNull DataSource dataSource) {
        this.retentionPolicyService = Objects.requireNonNull(retentionPolicyService);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void enforcePolicies() {
        LOG.info("Starting data retention enforcement");
        // Process all tenants. In a multi-tenant system we iterate all policies.
        List<RetentionPolicy> policies = retentionPolicyService.listEnabledPoliciesByTenant("*");
        // Actually we need to get all policies across all tenants. The repo doesn't have a findAllEnabled.
        // For now, we'll use a direct query to get all enabled policies.
        try {
            policies = jdbc.query(
                "SELECT * FROM retention_policies WHERE enabled = TRUE",
                (rs, rowNum) -> new RetentionPolicy(
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
                )
            );
        } catch (Exception e) {
            LOG.error("Failed to load retention policies", e);
            return;
        }

        for (RetentionPolicy policy : policies) {
            try {
                enforcePolicy(policy);
            } catch (Exception e) {
                LOG.error("Failed to enforce retention policy {}", policy.policyId(), e);
            }
        }
        LOG.info("Data retention enforcement complete");
    }

    private void enforcePolicy(@NonNull RetentionPolicy policy) {
        Instant cutoff = Instant.now().minusSeconds(policy.retentionDays() * 86400L);
        String table = resolveTableName(policy.resourceType());
        if (table == null) {
            LOG.warn("Unknown resource type for retention: {}", policy.resourceType());
            return;
        }

        String sql = "DELETE FROM " + table + " WHERE created_at < ?";
        int deleted = jdbc.update(sql, Timestamp.from(cutoff));
        LOG.info("Retention policy {}: deleted {} rows from {} older than {}",
            policy.policyId(), deleted, table, cutoff);

        jdbc.update(
            "UPDATE retention_policies SET last_run_at = ?, last_run_deleted = ? WHERE policy_id = ?",
            Timestamp.from(Instant.now()), deleted, policy.policyId());
    }

    private String resolveTableName(@NonNull String resourceType) {
        return switch (resourceType.toLowerCase()) {
            case "runs" -> "runs";
            case "spans" -> "spans";
            case "llm_calls" -> "llm_calls";
            case "tool_calls" -> "tool_calls";
            case "feedback" -> "feedback";
            case "eval_results" -> "eval_results";
            case "eval_runs" -> "eval_runs";
            case "alert_events" -> "alert_events";
            case "metric_snapshots" -> "metric_snapshots";
            case "audit_logs" -> "audit_logs";
            default -> null;
        };
    }
}
