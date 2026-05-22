package com.chorus.observe.retention;

import com.chorus.observe.model.RetentionPolicy;
import com.chorus.observe.persistence.RetentionPolicyRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RetentionPolicyService {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionPolicyService.class);

    private final RetentionPolicyRepository retentionPolicyRepository;

    public RetentionPolicyService(@NonNull RetentionPolicyRepository retentionPolicyRepository) {
        this.retentionPolicyRepository = Objects.requireNonNull(retentionPolicyRepository);
    }

    public @NonNull RetentionPolicy createPolicy(@NonNull String tenantId, @NonNull String name,
                                                  @NonNull String resourceType, int retentionDays,
                                                  boolean archiveEnabled, String archiveLocation) {
        String policyId = "ret-" + UUID.randomUUID().toString().substring(0, 8);
        RetentionPolicy policy = new RetentionPolicy(policyId, tenantId, name, resourceType, retentionDays,
            archiveEnabled, archiveLocation, true, null, 0, Instant.now(), Instant.now());
        retentionPolicyRepository.save(policy);
        LOG.info("Created retention policy: {} for tenant {}", name, tenantId);
        return policy;
    }

    public @NonNull Optional<RetentionPolicy> getPolicy(@NonNull String policyId) {
        return retentionPolicyRepository.findById(policyId);
    }

    public @NonNull List<RetentionPolicy> listPoliciesByTenant(@NonNull String tenantId) {
        return retentionPolicyRepository.findByTenant(tenantId);
    }

    public @NonNull List<RetentionPolicy> listEnabledPoliciesByTenant(@NonNull String tenantId) {
        return retentionPolicyRepository.findEnabledByTenant(tenantId);
    }

    public void deletePolicy(@NonNull String policyId) {
        retentionPolicyRepository.deleteById(policyId);
    }
}
