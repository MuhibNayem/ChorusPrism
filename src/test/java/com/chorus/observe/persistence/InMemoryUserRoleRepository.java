package com.chorus.observe.persistence;

import com.chorus.observe.model.UserRole;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryUserRoleRepository extends UserRoleRepository {
    private final List<UserRole> store = new ArrayList<>();

    public InMemoryUserRoleRepository() {
        super(null);
    }

    @Override
    public void save(@NonNull UserRole userRole) {
        store.removeIf(ur -> ur.userId().equals(userRole.userId()) && ur.roleId().equals(userRole.roleId()));
        store.add(userRole);
    }

    @Override
    public @NonNull List<UserRole> findByUserId(@NonNull String userId) {
        return store.stream()
            .filter(ur -> ur.userId().equals(userId))
            .toList();
    }

    @Override
    public @NonNull List<UserRole> findByRoleId(@NonNull String roleId) {
        return store.stream()
            .filter(ur -> ur.roleId().equals(roleId))
            .toList();
    }

    @Override
    public void deleteByUserId(@NonNull String userId) {
        store.removeIf(ur -> ur.userId().equals(userId));
    }

    @Override
    public void deleteByUserIdAndRoleId(@NonNull String userId, @NonNull String roleId) {
        store.removeIf(ur -> ur.userId().equals(userId) && ur.roleId().equals(roleId));
    }
}
