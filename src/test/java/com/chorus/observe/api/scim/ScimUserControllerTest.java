package com.chorus.observe.api.scim;

import com.chorus.observe.dto.scim.ScimListResponse;
import com.chorus.observe.dto.scim.ScimUserDto;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.InMemoryRoleRepository;
import com.chorus.observe.persistence.InMemoryUserRepository;
import com.chorus.observe.persistence.InMemoryUserRoleRepository;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.ScimUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScimUserControllerTest {

    private static final String TENANT_ID = "tenant-1";

    private InMemoryUserRepository userRepository;
    private InMemoryRoleRepository roleRepository;
    private InMemoryUserRoleRepository userRoleRepository;
    private ScimUserController controller;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        roleRepository = new InMemoryRoleRepository();
        userRoleRepository = new InMemoryUserRoleRepository();
        ScimUserService service = new ScimUserService(userRepository, userRoleRepository, roleRepository);
        controller = new ScimUserController(service);
        TenantContext.set(TENANT_ID, null, null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateUser() {
        ScimUserDto dto = new ScimUserDto(null, List.of(), "alice@corp.com", "Alice", true, null, null, null);
        ResponseEntity<?> response = controller.create(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ScimUserDto created = (ScimUserDto) response.getBody();
        assertThat(created).isNotNull();
        assertThat(created.userName()).isEqualTo("alice@corp.com");
        assertThat(created.id()).isNotNull();
    }

    @Test
    void shouldReturn409OnDuplicateEmail() {
        ScimUserDto dto = new ScimUserDto(null, List.of(), "bob@corp.com", "Bob", true, null, null, null);
        controller.create(dto);

        ResponseEntity<?> response = controller.create(dto);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldGetUserById() {
        ScimUserDto created = (ScimUserDto) controller.create(
            new ScimUserDto(null, List.of(), "carol@corp.com", "Carol", true, null, null, null)).getBody();

        ResponseEntity<?> response = controller.get(created.id());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ScimUserDto found = (ScimUserDto) response.getBody();
        assertThat(found.userName()).isEqualTo("carol@corp.com");
    }

    @Test
    void shouldReturn404ForMissingUser() {
        ResponseEntity<?> response = controller.get("nonexistent");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldListUsersWithFilter() {
        controller.create(new ScimUserDto(null, List.of(), "alice@corp.com", "Alice", true, null, null, null));
        controller.create(new ScimUserDto(null, List.of(), "bob@corp.com", "Bob", true, null, null, null));

        ResponseEntity<?> response = controller.list("userName eq \"alice@corp.com\"", 1, 100);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ScimListResponse list = (ScimListResponse) response.getBody();
        assertThat(list.totalResults()).isEqualTo(1);
        assertThat(list.Resources()).hasSize(1);
        assertThat(list.Resources().get(0).userName()).isEqualTo("alice@corp.com");
    }

    @Test
    void shouldSoftDeleteUser() {
        ScimUserDto created = (ScimUserDto) controller.create(
            new ScimUserDto(null, List.of(), "dave@corp.com", "Dave", true, null, null, null)).getBody();

        ResponseEntity<Void> response = controller.delete(created.id());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var userOpt = userRepository.findById(created.id());
        assertThat(userOpt).isPresent();
        assertThat(userOpt.get().status()).isEqualTo(User.Status.INACTIVE);
    }
}
