package com.chorus.observe.service;

import com.chorus.observe.model.TenantOauthConfig;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.RoleRepository;
import com.chorus.observe.persistence.TenantOauthConfigRepository;
import com.chorus.observe.persistence.UserRepository;
import com.chorus.observe.persistence.UserRoleRepository;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Oauth2ConfigService {

    private final TenantOauthConfigRepository configRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public Oauth2ConfigService(@NonNull TenantOauthConfigRepository configRepository,
                               @NonNull UserRepository userRepository,
                               @NonNull UserRoleRepository userRoleRepository,
                               @NonNull RoleRepository roleRepository) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    public @NonNull TenantOauthConfig create(@NonNull String tenantId,
                                              @NonNull String providerName,
                                              @NonNull String clientId,
                                              @NonNull String clientSecret,
                                              @NonNull String issuerUri,
                                              @NonNull List<String> scopes,
                                              @NonNull String defaultRole) {
        boolean hasLocalAdmin = userRepository.findByTenant(tenantId).stream()
            .anyMatch(u -> u.authSource() == User.AuthSource.LOCAL && hasAdminRole(u.userId()));

        if (!hasLocalAdmin) {
            throw new IllegalStateException(
                "At least one local admin must exist before enabling SSO. " +
                "Create a local admin user first.");
        }

        TenantOauthConfig config = new TenantOauthConfig(
            null, tenantId, providerName, clientId, clientSecret, issuerUri,
            scopes, defaultRole, true, Instant.now(), Instant.now());
        configRepository.save(config);
        return config;
    }

    public @NonNull Optional<TenantOauthConfig> findById(@NonNull UUID id) {
        return configRepository.findById(id);
    }

    public @NonNull List<TenantOauthConfig> findByTenant(@NonNull String tenantId) {
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
