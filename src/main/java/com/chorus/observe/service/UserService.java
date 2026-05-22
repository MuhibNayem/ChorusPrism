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
        String userId = "usr-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User(userId, tenantId, email.toLowerCase(), passwordEncoder.encode(rawPassword),
            displayName, User.Status.ACTIVE, null, Instant.now(), Instant.now());
        userRepository.save(user);
        LOG.info("Created user: {} in tenant {}", email, tenantId);
        return user;
    }

    public @NonNull Optional<User> authenticate(@NonNull String tenantId, @NonNull String email, @NonNull String rawPassword) {
        Optional<User> opt = userRepository.findByEmail(tenantId, email.toLowerCase());
        if (opt.isEmpty()) return Optional.empty();
        User user = opt.get();
        if (user.status() != User.Status.ACTIVE) return Optional.empty();
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) return Optional.empty();
        return opt;
    }

    public @NonNull Optional<User> getUser(@NonNull String userId) {
        return userRepository.findById(userId);
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

    public java.util.@NonNull List<String> getUserPermissions(@NonNull String userId) {
        return getUserRoles(userId).stream()
            .flatMap(r -> r.permissions().stream())
            .distinct()
            .toList();
    }
}
