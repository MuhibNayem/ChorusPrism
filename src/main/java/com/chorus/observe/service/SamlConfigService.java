package com.chorus.observe.service;

import com.chorus.observe.model.TenantSamlConfig;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.RoleRepository;
import com.chorus.observe.persistence.TenantSamlConfigRepository;
import com.chorus.observe.persistence.UserRepository;
import com.chorus.observe.persistence.UserRoleRepository;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SamlConfigService {

    private final TenantSamlConfigRepository configRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public SamlConfigService(@NonNull TenantSamlConfigRepository configRepository,
                             @NonNull UserRepository userRepository,
                             @NonNull UserRoleRepository userRoleRepository,
                             @NonNull RoleRepository roleRepository) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    public @NonNull TenantSamlConfig create(@NonNull String tenantId,
                                             @NonNull String providerName,
                                             @NonNull String entityId,
                                             @NonNull String signOnUrl,
                                             @NonNull String signingCertThumbprint,
                                             String metadataUrl,
                                             @NonNull String acsUrl,
                                             @NonNull String defaultRole) {
        boolean hasLocalAdmin = userRepository.findByTenant(tenantId).stream()
            .anyMatch(u -> u.authSource() == User.AuthSource.LOCAL && hasAdminRole(u.userId()));

        if (!hasLocalAdmin) {
            throw new IllegalStateException(
                "At least one local admin must exist before enabling SSO. " +
                "Create a local admin user first.");
        }

        TenantSamlConfig config = new TenantSamlConfig(
            null, tenantId, providerName, entityId, signOnUrl,
            signingCertThumbprint, metadataUrl, acsUrl,
            defaultRole, true, Instant.now(), Instant.now());
        configRepository.save(config);
        return config;
    }

    public @NonNull Optional<TenantSamlConfig> findById(@NonNull UUID id) {
        return configRepository.findById(id);
    }

    public @NonNull List<TenantSamlConfig> findByTenant(@NonNull String tenantId) {
        return configRepository.findByTenantId(tenantId);
    }

    public void delete(@NonNull UUID id) {
        configRepository.deleteById(id);
    }

    private boolean hasAdminRole(@NonNull String userId) {
        var userRoles = userRoleRepository.findByUserId(userId);
        for (var userRole : userRoles) {
            var roleOpt = roleRepository.findById(userRole.roleId());
            if (roleOpt.isPresent() && "admin".equalsIgnoreCase(roleOpt.get().name())) {
                return true;
            }
        }
        return false;
    }
}
