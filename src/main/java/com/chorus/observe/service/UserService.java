package com.chorus.observe.service;

import com.chorus.observe.model.Role;
import com.chorus.observe.model.User;
import com.chorus.observe.model.UserRole;
import com.chorus.observe.persistence.RoleRepository;
import com.chorus.observe.persistence.UserRepository;
import com.chorus.observe.persistence.UserRoleRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(@NonNull UserRepository userRepository, @NonNull RoleRepository roleRepository,
                       @NonNull UserRoleRepository userRoleRepository, @NonNull PasswordEncoder passwordEncoder) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    public @NonNull User createUser(@NonNull String tenantId, @NonNull String email, @NonNull String rawPassword,
                                    @NonNull String displayName) {
        // Full UUID avoids birthday-problem collision (vs 8-char truncated IDs)
        String userId = "usr-" + UUID.randomUUID();
        User user = new User(userId, tenantId, email.toLowerCase(), passwordEncoder.encode(rawPassword),
            displayName, User.Status.ACTIVE, null, User.AuthSource.LOCAL, Instant.now(), Instant.now());
        userRepository.save(user);
        LOG.info("Created user in tenant {}", tenantId);
        return user;
    }

    public @NonNull Optional<User> authenticate(@NonNull String tenantId, @NonNull String email, @NonNull String rawPassword) {
        Optional<User> opt = userRepository.findByEmailIgnoreCase(tenantId, email);
        if (opt.isEmpty()) return Optional.empty();
        User user = opt.get();
        if (user.status() != User.Status.ACTIVE) return Optional.empty();
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) return Optional.empty();
        return opt;
    }

    public @NonNull Optional<User> getUser(@NonNull String userId) {
        return userRepository.findById(userId);
    }

    public @NonNull Optional<User> findByEmail(@NonNull String tenantId, @NonNull String email) {
        return userRepository.findByEmailIgnoreCase(tenantId, email.toLowerCase());
    }

    public java.util.@NonNull List<User> listUsersByTenant(@NonNull String tenantId) {
        return userRepository.findByTenant(tenantId);
    }

    public void assignRole(@NonNull String userId, @NonNull String roleId) {
        userRoleRepository.save(new UserRole(userId, roleId, Instant.now()));
    }

    public java.util.@NonNull List<Role> getUserRoles(@NonNull String userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        return userRoles.stream()
            .map(ur -> roleRepository.findById(ur.roleId()))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * Returns all permissions for a user in a single JOIN query (no N+1).
     */
    public java.util.@NonNull List<String> getUserPermissions(@NonNull String userId) {
        return userRoleRepository.findPermissionsByUserId(userId);
    }

    public void updateLastLoginAt(@NonNull String userId) {
        Instant now = Instant.now();
        userRepository.findById(userId).ifPresent(user -> {
            User updated = new User(user.userId(), user.tenantId(), user.email(), user.passwordHash(),
                user.displayName(), user.status(), now, user.authSource(), user.createdAt(), now);
            userRepository.save(updated);
        });
    }

    public void updatePassword(@NonNull String userId, @NonNull String newRawPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            User updated = new User(user.userId(), user.tenantId(), user.email(),
                passwordEncoder.encode(newRawPassword), user.displayName(), user.status(),
                user.lastLoginAt(), user.authSource(), user.createdAt(), Instant.now());
            userRepository.save(updated);
        });
    }
}
