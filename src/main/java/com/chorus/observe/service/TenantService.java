package com.chorus.observe.service;

import com.chorus.observe.model.Tenant;
import com.chorus.observe.persistence.TenantRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class TenantService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;

    public TenantService(@NonNull TenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
    }

    public @NonNull Tenant createTenant(@NonNull String name) {
        String tenantId = "tnt-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = new Tenant(tenantId, name, Map.of(), Tenant.Status.ACTIVE, Instant.now(), Instant.now());
        tenantRepository.save(tenant);
        LOG.info("Created tenant: {} ({})", tenantId, name);
        return tenant;
    }

    public @NonNull Optional<Tenant> getTenant(@NonNull String tenantId) {
        return tenantRepository.findById(tenantId);
    }

    public java.util.@NonNull List<Tenant> listTenants() {
        return tenantRepository.findAll();
    }
}
