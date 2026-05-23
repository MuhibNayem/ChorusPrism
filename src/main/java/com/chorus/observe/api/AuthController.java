package com.chorus.observe.api;

import com.chorus.observe.service.AuthenticationService;
import com.chorus.observe.service.UserService;
import com.chorus.observe.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public authentication endpoints — login, registration, password reset.
 * These endpoints are exempt from JWT/API key filters.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    public AuthController(@NonNull AuthenticationService authenticationService, @NonNull UserService userService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request) {
        var result = authenticationService.login(request.tenantId(), request.email(), request.password());
        if (result == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        return ResponseEntity.ok(Map.of(
            "token", result.token(),
            "userId", result.user().userId(),
            "email", result.user().email(),
            "displayName", result.user().displayName(),
            "tenantId", result.user().tenantId(),
            "permissions", result.permissions()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
        String tenantId = "tnt-" + UUID.randomUUID().toString().substring(0, 8);
        var user = userService.createUser(tenantId, request.email(), request.password(), request.displayName());
        userService.assignRole(user.userId(), "role-admin");
        var result = authenticationService.login(tenantId, request.email(), request.password());
        return ResponseEntity.ok(Map.of(
            "token", result.token(),
            "userId", user.userId(),
            "email", user.email(),
            "displayName", user.displayName(),
            "tenantId", tenantId,
            "permissions", result.permissions()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        String userId = TenantContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        var userOpt = userService.getUser(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        var user = userOpt.get();
        var permissions = userService.getUserPermissions(userId);
        return ResponseEntity.ok(Map.of(
            "userId", user.userId(),
            "email", user.email(),
            "displayName", user.displayName(),
            "tenantId", user.tenantId(),
            "permissions", permissions
        ));
    }

    public record LoginRequest(
        @NotBlank String tenantId,
        @NotBlank @Email String email,
        @NotBlank String password
    ) {}

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank String displayName
    ) {}
}
