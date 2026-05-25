package com.chorus.observe.api;

import com.chorus.observe.model.RetentionPolicy;
import com.chorus.observe.retention.RetentionPolicyService;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/retention-policies")
public class RetentionPolicyController {

    private static final int MAX_RETENTION_DAYS = 3650; // 10 years for pct computation

    private final RetentionPolicyService retentionPolicyService;

    public RetentionPolicyController(@NonNull RetentionPolicyService retentionPolicyService) {
        this.retentionPolicyService = retentionPolicyService;
    }

    @PostMapping
    public ResponseEntity<?> createPolicy(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        String name = (String) request.get("name");
        String resourceType = (String) request.get("resourceType");
        int retentionDays = (int) request.getOrDefault("retentionDays", 30);
        boolean archiveEnabled = (boolean) request.getOrDefault("archiveEnabled", false);
        String archiveLocation = (String) request.get("archiveLocation");
        RetentionPolicy policy = retentionPolicyService.createPolicy(tenantId, name, resourceType, retentionDays,
            archiveEnabled, archiveLocation);
        return ResponseEntity.ok(Map.of("policyId", policy.policyId()));
    }

    @GetMapping
    public ResponseEntity<List<RetentionPolicySummary>> listPolicies() {
        String tenantId;
        try {
            tenantId = TenantContext.getTenantId();
        } catch (Exception e) {
            tenantId = "default";
        }
        List<RetentionPolicy> policies = retentionPolicyService.listPoliciesByTenant(tenantId);
        List<RetentionPolicySummary> summaries = policies.stream()
            .map(RetentionPolicySummary::from)
            .toList();
        return ResponseEntity.ok(summaries);
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<?> deletePolicy(@PathVariable String policyId) {
        retentionPolicyService.deletePolicy(policyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Frontend-facing retention policy summary.
     * Maps resource_type → tier label, retentionDays → human duration, and computes pct.
     */
    public record RetentionPolicySummary(String tier, String duration, double pct) {

        static RetentionPolicySummary from(RetentionPolicy p) {
            String tier = toTierLabel(p.resourceType(), p.name());
            String duration = toDurationLabel(p.retentionDays());
            double pct = Math.min(1.0, (double) p.retentionDays() / MAX_RETENTION_DAYS);
            return new RetentionPolicySummary(tier, duration, pct);
        }

        private static String toTierLabel(String resourceType, String name) {
            if (resourceType == null) return name;
            return switch (resourceType.toLowerCase()) {
                case "runs", "spans", "traces" -> "Traces";
                case "llm_calls", "llm calls", "llm i/o" -> "LLM I/O";
                case "tool_calls", "tool calls", "tool i/o" -> "Tool I/O";
                case "annotations", "eval_results", "feedback" -> "Annotations";
                default -> name != null ? name : resourceType;
            };
        }

        private static String toDurationLabel(int days) {
            if (days >= 365) {
                int years = days / 365;
                return years == 1 ? "1 year" : years + " years";
            }
            return days + " days";
        }
    }
}
