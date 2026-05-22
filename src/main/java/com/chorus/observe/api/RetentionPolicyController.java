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
    public ResponseEntity<List<RetentionPolicy>> listPolicies() {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(retentionPolicyService.listPoliciesByTenant(tenantId));
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<?> deletePolicy(@PathVariable String policyId) {
        retentionPolicyService.deletePolicy(policyId);
        return ResponseEntity.noContent().build();
    }
}
