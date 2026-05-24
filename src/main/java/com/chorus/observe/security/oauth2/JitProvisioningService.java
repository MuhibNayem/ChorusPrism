package com.chorus.observe.security.oauth2;

import com.chorus.observe.model.User;
import com.chorus.observe.persistence.RoleRepository;
import com.chorus.observe.persistence.UserRepository;
import com.chorus.observe.persistence.UserRoleRepository;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public class JitProvisioningService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public JitProvisioningService(@NonNull UserRepository userRepository,
                                  @NonNull UserRoleRepository userRoleRepository,
                                  @NonNull RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    public @NonNull User provisionOrLink(@NonNull String tenantId,
                                         @NonNull String email,
                                         @NonNull String displayName,
                                         User.AuthSource authSource,
                                         @NonNull String defaultRole) {
        return userRepository.findByEmailIgnoreCase(tenantId, email)
            .map(existing -> {
                User updated = new User(
                    existing.userId(), existing.tenantId(), existing.email(),
                    existing.passwordHash(),
                    displayName != null && !displayName.isBlank() ? displayName : existing.displayName(),
                    existing.status(), Instant.now(), existing.authSource(),
                    existing.createdAt(), Instant.now()
                );
                userRepository.save(updated);
                return updated;
            })
            .orElseGet(() -> {
                String userId = UUID.randomUUID().toString();
                User newUser = new User(
                    userId, tenantId, email, "", displayName,
                    User.Status.ACTIVE, Instant.now(), authSource,
                    Instant.now(), Instant.now()
                );
                userRepository.save(newUser);

                roleRepository.findByTenantIdAndName(tenantId, defaultRole)
                    .ifPresent(role -> userRoleRepository.save(
                        new com.chorus.observe.model.UserRole(userId, role.roleId(), Instant.now())));

                return newUser;
            });
    }
}
