package com.chorus.observe.service;

import com.chorus.observe.model.Role;
import com.chorus.observe.persistence.RoleRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RoleService {

    private static final Logger LOG = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository roleRepository;

    public RoleService(@NonNull RoleRepository roleRepository) {
        this.roleRepository = Objects.requireNonNull(roleRepository);
    }

    public @NonNull Role createRole(@NonNull String tenantId, @NonNull String name,
                                    @NonNull List<String> permissions, @Nullable String description) {
        String roleId = "role-" + UUID.randomUUID().toString().substring(0, 8);
        Role role = new Role(roleId, tenantId, name, permissions, description, Instant.now(), Instant.now());
        roleRepository.save(role);
        LOG.info("Created role: {} in tenant {}", name, tenantId);
        return role;
    }

    public @NonNull Optional<Role> getRole(@NonNull String roleId) {
        return roleRepository.findById(roleId);
    }

    public java.util.@NonNull List<Role> listRolesByTenant(@NonNull String tenantId) {
        return roleRepository.findByTenant(tenantId);
    }

    public void deleteRole(@NonNull String roleId) {
        roleRepository.deleteById(roleId);
    }
}
