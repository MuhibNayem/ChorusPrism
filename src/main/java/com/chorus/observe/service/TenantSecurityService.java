package com.chorus.observe.service;

import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Centralized tenant ownership validation to prevent IDOR vulnerabilities.
 * <p>
 * Every controller that accesses a resource by ID should verify that the
 * resource belongs to the currently authenticated tenant before returning it.
 * <p>
 * For resources that join through {@code runs} (spans, llm_calls, tool_calls,
 * feedback, checkpoints, etc.), ownership is verified via the run's tenant_id.
 */
public class TenantSecurityService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantSecurityService.class);

    private final JdbcTemplate jdbc;

    public TenantSecurityService(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    /**
     * Asserts that a run belongs to the current tenant.
     *
     * @param runId the run ID to check
     * @throws IllegalArgumentException if the run does not belong to the current tenant
     */
    public void assertRunBelongsToTenant(@NonNull String runId) {
        String tenantId = TenantContext.getTenantId();
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM runs WHERE run_id = ? AND tenant_id = ?",
            Long.class, runId, tenantId);
        if (count == null || count == 0) {
            LOG.warn("Tenant {} attempted unauthorized access to run {}", tenantId, runId);
            throw new IllegalArgumentException("Run not found or access denied");
        }
    }

    /**
     * Asserts that a dataset belongs to the current tenant.
     *
     * @param datasetId the dataset ID to check
     * @throws IllegalArgumentException if the dataset does not belong to the current tenant
     */
    public void assertDatasetBelongsToTenant(@NonNull String datasetId) {
        String tenantId = TenantContext.getTenantId();
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM datasets WHERE dataset_id = ? AND tenant_id = ?",
            Long.class, datasetId, tenantId);
        if (count == null || count == 0) {
            LOG.warn("Tenant {} attempted unauthorized access to dataset {}", tenantId, datasetId);
            throw new IllegalArgumentException("Dataset not found or access denied");
        }
    }

    /**
     * Asserts that an eval run belongs to the current tenant.
     *
     * @param evalRunId the eval run ID to check
     * @throws IllegalArgumentException if the eval run does not belong to the current tenant
     */
    public void assertEvalRunBelongsToTenant(@NonNull String evalRunId) {
        String tenantId = TenantContext.getTenantId();
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM eval_runs WHERE eval_run_id = ? AND tenant_id = ?",
            Long.class, evalRunId, tenantId);
        if (count == null || count == 0) {
            LOG.warn("Tenant {} attempted unauthorized access to eval run {}", tenantId, evalRunId);
            throw new IllegalArgumentException("Eval run not found or access denied");
        }
    }

    /**
     * Asserts that an alert rule belongs to the current tenant.
     *
     * @param ruleId the alert rule ID to check
     * @throws IllegalArgumentException if the rule does not belong to the current tenant
     */
    public void assertAlertRuleBelongsToTenant(@NonNull String ruleId) {
        String tenantId = TenantContext.getTenantId();
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_rules WHERE rule_id = ? AND tenant_id = ?",
            Long.class, ruleId, tenantId);
        if (count == null || count == 0) {
            LOG.warn("Tenant {} attempted unauthorized access to alert rule {}", tenantId, ruleId);
            throw new IllegalArgumentException("Alert rule not found or access denied");
        }
    }

    /**
     * Asserts that a prompt version belongs to the current tenant.
     *
     * @param versionId the prompt version ID to check
     * @throws IllegalArgumentException if the version does not belong to the current tenant
     */
    public void assertPromptVersionBelongsToTenant(@NonNull String versionId) {
        String tenantId = TenantContext.getTenantId();
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM prompt_versions WHERE version_id = ? AND tenant_id = ?",
            Long.class, versionId, tenantId);
        if (count == null || count == 0) {
            LOG.warn("Tenant {} attempted unauthorized access to prompt version {}", tenantId, versionId);
            throw new IllegalArgumentException("Prompt version not found or access denied");
        }
    }

    /**
     * Asserts that a resource in a table with a direct tenant_id column belongs
     * to the current tenant.
     *
     * @param table     the table name (must be validated/safe)
     * @param idColumn  the ID column name
     * @param idValue   the resource ID
     * @throws IllegalArgumentException if the resource does not belong to the current tenant
     */
    public void assertBelongsToTenant(@NonNull String table, @NonNull String idColumn, @NonNull String idValue) {
        String tenantId = TenantContext.getTenantId();
        Long count;
        try {
            count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = ? AND tenant_id = ?",
                Long.class, idValue, tenantId);
        } catch (EmptyResultDataAccessException e) {
            count = 0L;
        }
        if (count == null || count == 0) {
            LOG.warn("Tenant {} attempted unauthorized access to {}.{}", tenantId, table, idValue);
            throw new IllegalArgumentException("Resource not found or access denied");
        }
    }
}
