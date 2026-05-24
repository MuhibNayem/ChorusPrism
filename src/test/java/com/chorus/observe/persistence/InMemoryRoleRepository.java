package com.chorus.observe.persistence;

import com.chorus.observe.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class InMemoryRoleRepository extends RoleRepository {
    private final Map<String, Role> store = new HashMap<>();

    public InMemoryRoleRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(@NonNull Role role) {
        store.put(role.roleId(), role);
    }

    @Override
    public @NonNull Optional<Role> findById(@NonNull String roleId) {
        return Optional.ofNullable(store.get(roleId));
    }

    @Override
    public @NonNull Optional<Role> findByTenantIdAndName(@NonNull String tenantId, @NonNull String name) {
        return store.values().stream()
            .filter(r -> r.tenantId().equals(tenantId) && r.name().equalsIgnoreCase(name))
            .findFirst();
    }

    @Override
    public @NonNull List<Role> findByTenant(@NonNull String tenantId) {
        return store.values().stream()
            .filter(r -> r.tenantId().equals(tenantId))
            .toList();
    }

    @Override
    public void deleteById(@NonNull String roleId) {
        store.remove(roleId);
    }
}
