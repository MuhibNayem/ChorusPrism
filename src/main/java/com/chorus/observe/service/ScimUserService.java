package com.chorus.observe.service;

import com.chorus.observe.dto.scim.ScimUserDto;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.RoleRepository;
import com.chorus.observe.persistence.UserRepository;
import com.chorus.observe.persistence.UserRoleRepository;
import com.chorus.observe.security.scim.ScimFilterParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ScimUserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public ScimUserService(@NonNull UserRepository userRepository,
                           @NonNull UserRoleRepository userRoleRepository,
                           @NonNull RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    public @NonNull ScimUserDto createUser(@NonNull String tenantId, @NonNull ScimUserDto dto) {
        String email = dto.userName();

        if (userRepository.findByEmailIgnoreCase(tenantId, email).isPresent()) {
            throw new ScimConflictException("User with email " + email + " already exists");
        }

        String userId = UUID.randomUUID().toString();
        String displayName = dto.displayName() != null ? dto.displayName() : email;

        User user = new User(
            userId, tenantId, email, "", displayName,
            dto.active() ? User.Status.ACTIVE : User.Status.INACTIVE,
            Instant.now(), User.AuthSource.SAML,
            Instant.now(), Instant.now()
        );
        userRepository.save(user);

        return toDto(user);
    }

    public @NonNull Optional<ScimUserDto> getUser(@NonNull String tenantId, @NonNull String userId) {
        return userRepository.findById(userId)
            .filter(u -> u.tenantId().equals(tenantId))
            .map(this::toDto);
    }

    public @NonNull List<ScimUserDto> listUsers(@NonNull String tenantId,
                                                 @Nullable String filter,
                                                 int startIndex,
                                                 int count) {
        List<User> users = userRepository.findByTenant(tenantId);

        if (filter != null && !filter.isBlank()) {
            List<ScimFilterParser.Filter> filters = ScimFilterParser.parse(filter);
            for (ScimFilterParser.Filter f : filters) {
                if ("userName".equals(f.attribute()) && "eq".equals(f.operator())) {
                    users = users.stream()
                        .filter(u -> u.email().equalsIgnoreCase(f.value()))
                        .toList();
                }
            }
        }

        int total = users.size();
        int from = Math.max(0, startIndex - 1);
        int to = Math.min(from + count, total);
        return users.subList(from, to).stream()
            .map(this::toDto)
            .toList();
    }

    public int countUsers(@NonNull String tenantId, @Nullable String filter) {
        return listUsers(tenantId, filter, 1, Integer.MAX_VALUE).size();
    }

    public @NonNull ScimUserDto updateUser(@NonNull String tenantId, @NonNull String userId,
                                            @NonNull ScimUserDto dto) {
        User existing = userRepository.findById(userId)
            .filter(u -> u.tenantId().equals(tenantId))
            .orElseThrow(() -> new ScimNotFoundException("User not found: " + userId));

        User updated = new User(
            existing.userId(), existing.tenantId(),
            dto.userName() != null ? dto.userName() : existing.email(),
            existing.passwordHash(),
            dto.displayName() != null ? dto.displayName() : existing.displayName(),
            dto.active() ? User.Status.ACTIVE : User.Status.INACTIVE,
            existing.lastLoginAt(), existing.authSource(),
            existing.createdAt(), Instant.now()
        );
        userRepository.save(updated);
        return toDto(updated);
    }

    @Transactional
    public void deactivateUser(@NonNull String tenantId, @NonNull String userId) {
        User existing = userRepository.findById(userId)
            .filter(u -> u.tenantId().equals(tenantId))
            .orElseThrow(() -> new ScimNotFoundException("User not found: " + userId));

        User deactivated = new User(
            existing.userId(), existing.tenantId(), existing.email(),
            existing.passwordHash(), existing.displayName(),
            User.Status.INACTIVE, existing.lastLoginAt(), existing.authSource(),
            existing.createdAt(), Instant.now()
        );
        userRepository.save(deactivated);
    }

    private @NonNull ScimUserDto toDto(@NonNull User user) {
        return new ScimUserDto(
            user.userId(),
            List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
            user.email(),
            user.displayName(),
            user.status() == User.Status.ACTIVE,
            user.displayName() != null ? java.util.Map.of("formatted", user.displayName()) : null,
            List.of(new ScimUserDto.Email(user.email(), true)),
            new ScimUserDto.Meta("User", user.createdAt().toString(), user.updatedAt().toString())
        );
    }
}
