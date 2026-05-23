package com.chorus.observe.api;

import com.chorus.observe.model.User;
import com.chorus.observe.service.UserService;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(@NonNull UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> request) {
        String tenantId = TenantContext.getTenantId();
        User user = userService.createUser(tenantId, request.get("email"), request.get("password"), request.get("displayName"));
        return ResponseEntity.ok(Map.of("userId", user.userId(), "email", user.email()));
    }

    @GetMapping
    public ResponseEntity<List<User>> listUsers() {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(userService.listUsersByTenant(tenantId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable String userId) {
        return userService.getUser(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}/roles/{roleId}")
    public ResponseEntity<?> assignRole(@PathVariable String userId, @PathVariable String roleId) {
        userService.assignRole(userId, roleId);
        return ResponseEntity.ok(Map.of("message", "Role assigned"));
    }
}
