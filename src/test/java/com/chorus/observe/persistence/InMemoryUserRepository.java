package com.chorus.observe.persistence;

import com.chorus.observe.model.User;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class InMemoryUserRepository extends UserRepository {
    private final Map<String, User> store = new HashMap<>();

    public InMemoryUserRepository() {
        super(null);
    }

    @Override
    public void save(@NonNull User user) {
        store.put(user.userId(), user);
    }

    @Override
    public @NonNull Optional<User> findById(@NonNull String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public @NonNull Optional<User> findByEmail(@NonNull String tenantId, @NonNull String email) {
        return store.values().stream()
            .filter(u -> u.tenantId().equals(tenantId) && u.email().equals(email))
            .findFirst();
    }

    @Override
    public @NonNull Optional<User> findByEmailIgnoreCase(@NonNull String tenantId, @NonNull String email) {
        return store.values().stream()
            .filter(u -> u.tenantId().equals(tenantId) && u.email().equalsIgnoreCase(email))
            .findFirst();
    }

    @Override
    public @NonNull List<User> findByTenant(@NonNull String tenantId) {
        return store.values().stream()
            .filter(u -> u.tenantId().equals(tenantId))
            .sorted(Comparator.comparing(User::createdAt).reversed())
            .toList();
    }

    @Override
    public void deleteById(@NonNull String userId) {
        store.remove(userId);
    }
}
