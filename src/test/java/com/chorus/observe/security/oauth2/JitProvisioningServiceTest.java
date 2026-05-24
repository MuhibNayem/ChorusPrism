package com.chorus.observe.security.oauth2;

import com.chorus.observe.model.Role;
import com.chorus.observe.model.User;
import com.chorus.observe.model.UserRole;
import com.chorus.observe.persistence.InMemoryRoleRepository;
import com.chorus.observe.persistence.InMemoryUserRepository;
import com.chorus.observe.persistence.InMemoryUserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JitProvisioningServiceTest {

    private InMemoryUserRepository userRepository;
    private InMemoryUserRoleRepository userRoleRepository;
    private InMemoryRoleRepository roleRepository;
    private JitProvisioningService service;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        userRoleRepository = new InMemoryUserRoleRepository();
        roleRepository = new InMemoryRoleRepository();
        service = new JitProvisioningService(userRepository, userRoleRepository, roleRepository);

        roleRepository.save(new Role("role-viewer", "tenant-1", "VIEWER", List.of("runs:read"), null, Instant.now(), Instant.now()));
        roleRepository.save(new Role("role-admin", "tenant-1", "ADMIN", List.of("admin"), null, Instant.now(), Instant.now()));
    }

    @Test
    void shouldCreateNewUserOnFirstLogin() {
        User user = service.provisionOrLink("tenant-1", "alice@corp.com", "Alice", User.AuthSource.OAUTH2, "VIEWER");

        assertThat(user.userId()).isNotBlank();
        assertThat(user.email()).isEqualTo("alice@corp.com");
        assertThat(user.authSource()).isEqualTo(User.AuthSource.OAUTH2);
        assertThat(user.passwordHash()).isEqualTo("");
        assertThat(user.status()).isEqualTo(User.Status.ACTIVE);

        var roles = userRoleRepository.findByUserId(user.userId());
        assertThat(roles).hasSize(1);
        assertThat(roleRepository.findById(roles.get(0).roleId())).isPresent();
        assertThat(roleRepository.findById(roles.get(0).roleId()).get().name()).isEqualTo("VIEWER");
    }

    @Test
    void shouldLinkExistingLocalUser() {
        String userId = UUID.randomUUID().toString();
        User localUser = new User(userId, "tenant-1", "bob@corp.com", "hashed", "Bob",
            User.Status.ACTIVE, null, User.AuthSource.LOCAL, Instant.now(), Instant.now());
        userRepository.save(localUser);

        User linked = service.provisionOrLink("tenant-1", "bob@corp.com", "Bob Updated", User.AuthSource.OAUTH2, "VIEWER");

        assertThat(linked.userId()).isEqualTo(userId);
        assertThat(linked.authSource()).isEqualTo(User.AuthSource.LOCAL);
        assertThat(linked.passwordHash()).isEqualTo("hashed");
        assertThat(linked.displayName()).isEqualTo("Bob Updated");
    }

    @Test
    void shouldPreserveExistingRoles() {
        String userId = UUID.randomUUID().toString();
        User localUser = new User(userId, "tenant-1", "charlie@corp.com", "hashed", "Charlie",
            User.Status.ACTIVE, null, User.AuthSource.LOCAL, Instant.now(), Instant.now());
        userRepository.save(localUser);
        userRoleRepository.save(new UserRole(userId, "role-admin", Instant.now()));

        service.provisionOrLink("tenant-1", "charlie@corp.com", "Charlie", User.AuthSource.OAUTH2, "VIEWER");

        var roles = userRoleRepository.findByUserId(userId);
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).roleId()).isEqualTo("role-admin");
    }

    @Test
    void shouldAssignDefaultRoleToJitUser() {
        User user = service.provisionOrLink("tenant-1", "dave@corp.com", "Dave", User.AuthSource.OAUTH2, "VIEWER");

        var roles = userRoleRepository.findByUserId(user.userId());
        assertThat(roles).hasSize(1);
        assertThat(roleRepository.findById(roles.get(0).roleId()).get().name()).isEqualTo("VIEWER");
    }

    @Test
    void shouldAssignDifferentDefaultRole() {
        User user = service.provisionOrLink("tenant-1", "eve@corp.com", "Eve", User.AuthSource.SAML, "ADMIN");

        var roles = userRoleRepository.findByUserId(user.userId());
        assertThat(roles).hasSize(1);
        assertThat(roleRepository.findById(roles.get(0).roleId()).get().name()).isEqualTo("ADMIN");
    }
}
