package com.chorus.observe.service;

import com.chorus.observe.model.RoleMapping;
import com.chorus.observe.model.TenantOauthConfig;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.RoleRepository;
import com.chorus.observe.persistence.TenantOauthConfigRepository;
import com.chorus.observe.persistence.UserRepository;
import com.chorus.observe.persistence.UserRoleRepository;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
                                              @NonNull String defaultRole,
                                              @NonNull List<RoleMapping> roleMappings,
                                              @NonNull List<String> allowedDomains,
                                              @NonNull Map<String, String> attributeMappings) {
        requireLocalAdmin(tenantId);
        TenantOauthConfig config = new TenantOauthConfig(
            null, tenantId, providerName, clientId, clientSecret, issuerUri,
            scopes, defaultRole, true,
            roleMappings, allowedDomains, attributeMappings,
            Instant.now(), Instant.now());
        configRepository.save(config);
        return config;
    }

    public @NonNull TenantOauthConfig update(@NonNull UUID id,
                                              @NonNull String tenantId,
                                              @NonNull String providerName,
                                              @NonNull String clientId,
                                              @NonNull String clientSecret,
                                              @NonNull String issuerUri,
                                              @NonNull List<String> scopes,
                                              @NonNull String defaultRole,
                                              @NonNull List<RoleMapping> roleMappings,
                                              @NonNull List<String> allowedDomains,
                                              @NonNull Map<String, String> attributeMappings) {
        TenantOauthConfig existing = configRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("OAuth2 config not found: " + id));

        TenantOauthConfig updated = new TenantOauthConfig(
            id, tenantId, providerName, clientId, clientSecret, issuerUri,
            scopes, defaultRole, existing.enabled(),
            roleMappings, allowedDomains, attributeMappings,
            existing.createdAt(), Instant.now());
        configRepository.save(updated);
        return updated;
    }

    public void toggle(@NonNull UUID id, boolean enabled) {
        TenantOauthConfig existing = configRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("OAuth2 config not found: " + id));

        TenantOauthConfig toggled = new TenantOauthConfig(
            existing.id(), existing.tenantId(), existing.providerName(),
            existing.clientId(), existing.clientSecret(), existing.issuerUri(),
            existing.scopes(), existing.defaultRole(), enabled,
            existing.roleMappings(), existing.allowedDomains(), existing.attributeMappings(),
            existing.createdAt(), Instant.now());
        configRepository.save(toggled);
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

    private void requireLocalAdmin(@NonNull String tenantId) {
        boolean hasLocalAdmin = userRepository.findByTenant(tenantId).stream()
            .anyMatch(u -> u.authSource() == User.AuthSource.LOCAL && hasAdminRole(u.userId()));
        if (!hasLocalAdmin) {
            throw new IllegalStateException(
                "At least one local admin must exist before enabling SSO. " +
                "Create a local admin user first.");
        }
    }

    private boolean hasAdminRole(@NonNull String userId) {
        for (var userRole : userRoleRepository.findByUserId(userId)) {
            var roleOpt = roleRepository.findById(userRole.roleId());
            if (roleOpt.isPresent() && "admin".equalsIgnoreCase(roleOpt.get().name())) return true;
        }
        return false;
    }
}
