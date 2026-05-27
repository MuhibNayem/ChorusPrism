package com.chorus.observe.service;

import com.chorus.observe.model.RoleMapping;
import com.chorus.observe.model.TenantSamlConfig;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.RoleRepository;
import com.chorus.observe.persistence.TenantSamlConfigRepository;
import com.chorus.observe.persistence.UserRepository;
import com.chorus.observe.persistence.UserRoleRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
                                             @Nullable String metadataUrl,
                                             @NonNull String acsUrl,
                                             @NonNull String defaultRole,
                                             @NonNull List<RoleMapping> roleMappings,
                                             @NonNull List<String> allowedDomains,
                                             @NonNull Map<String, String> attributeMappings,
                                             @Nullable String idpCertPem,
                                             @Nullable String idpMetadataXml) {
        requireLocalAdmin(tenantId);
        TenantSamlConfig config = new TenantSamlConfig(
            null, tenantId, providerName, entityId, signOnUrl,
            signingCertThumbprint, metadataUrl, acsUrl,
            defaultRole, true,
            roleMappings, allowedDomains, attributeMappings,
            idpCertPem, idpMetadataXml,
            Instant.now(), Instant.now());
        configRepository.save(config);
        return config;
    }

    public @NonNull TenantSamlConfig update(@NonNull UUID id,
                                             @NonNull String tenantId,
                                             @NonNull String providerName,
                                             @NonNull String entityId,
                                             @NonNull String signOnUrl,
                                             @NonNull String signingCertThumbprint,
                                             @Nullable String metadataUrl,
                                             @NonNull String acsUrl,
                                             @NonNull String defaultRole,
                                             @NonNull List<RoleMapping> roleMappings,
                                             @NonNull List<String> allowedDomains,
                                             @NonNull Map<String, String> attributeMappings,
                                             @Nullable String idpCertPem,
                                             @Nullable String idpMetadataXml) {
        TenantSamlConfig existing = configRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SAML config not found: " + id));

        TenantSamlConfig updated = new TenantSamlConfig(
            id, tenantId, providerName, entityId, signOnUrl,
            signingCertThumbprint, metadataUrl, acsUrl,
            defaultRole, existing.enabled(),
            roleMappings, allowedDomains, attributeMappings,
            idpCertPem, idpMetadataXml,
            existing.createdAt(), Instant.now());
        configRepository.save(updated);
        return updated;
    }

    public void toggle(@NonNull UUID id, boolean enabled) {
        TenantSamlConfig existing = configRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SAML config not found: " + id));

        TenantSamlConfig toggled = new TenantSamlConfig(
            existing.id(), existing.tenantId(), existing.providerName(),
            existing.entityId(), existing.signOnUrl(), existing.signingCertThumbprint(),
            existing.metadataUrl(), existing.acsUrl(), existing.defaultRole(), enabled,
            existing.roleMappings(), existing.allowedDomains(), existing.attributeMappings(),
            existing.idpCertPem(), existing.idpMetadataXml(),
            existing.createdAt(), Instant.now());
        configRepository.save(toggled);
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
