package com.chorus.observe.api;

import com.chorus.observe.service.AuthenticationService;
import com.chorus.observe.service.UserService;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

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
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String tenantId = request.get("tenantId");
        String email = request.get("email");
        String password = request.get("password");
        if (tenantId == null || email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantId, email, and password are required"));
        }
        var result = authenticationService.login(tenantId, email, password);
        if (result == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        return ResponseEntity.ok(Map.of(
            "token", result.token(),
            "userId", result.user().userId(),
            "email", result.user().email(),
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
}
