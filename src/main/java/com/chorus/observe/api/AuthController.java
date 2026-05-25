package com.chorus.observe.api;

import com.chorus.observe.model.Tenant;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.PasswordResetRepository;
import com.chorus.observe.persistence.RefreshTokenRepository;
import com.chorus.observe.persistence.TenantRepository;
import com.chorus.observe.persistence.TokenBlacklistRepository;
import com.chorus.observe.security.JwtTokenService;
import com.chorus.observe.security.LoginAttemptService;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.AuthenticationService;
import com.chorus.observe.service.RoleService;
import com.chorus.observe.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public authentication endpoints — login, registration, refresh, logout,
 * forgot-password, and reset-password. JWT is set as an httpOnly cookie
 * ({@code chorus_session}) in addition to being returned in the JSON body
 * so that Next.js API routes can relay it.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);
    private static final String SESSION_COOKIE = "chorus_session";
    private static final String REFRESH_COOKIE = "chorus_refresh";
    private static final int ACCESS_MAX_AGE  = (int) java.time.Duration.ofHours(1).getSeconds();
    private static final int REFRESH_MAX_AGE = (int) java.time.Duration.ofDays(7).getSeconds();

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final TenantRepository tenantRepository;
    private final RoleService roleService;
    private final TokenBlacklistRepository blacklist;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final LoginAttemptService loginAttemptService;
    private final JwtTokenService jwtTokenService;

    public AuthController(@NonNull AuthenticationService authenticationService,
                          @NonNull UserService userService,
                          @NonNull TenantRepository tenantRepository,
                          @NonNull RoleService roleService,
                          @NonNull TokenBlacklistRepository blacklist,
                          @NonNull RefreshTokenRepository refreshTokenRepository,
                          @NonNull PasswordResetRepository passwordResetRepository,
                          @NonNull LoginAttemptService loginAttemptService,
                          @NonNull JwtTokenService jwtTokenService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
        this.tenantRepository = tenantRepository;
        this.roleService = roleService;
        this.blacklist = blacklist;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.loginAttemptService = loginAttemptService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request,
                                   HttpServletResponse response) {
        if (loginAttemptService.isLocked(request.email(), request.tenantId())) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Account temporarily locked due to too many failed attempts. Try again later."));
        }

        var result = authenticationService.login(request.tenantId(), request.email(), request.password());
        if (result == null) {
            loginAttemptService.recordFailure(request.email(), request.tenantId());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        loginAttemptService.recordSuccess(request.email(), request.tenantId());
        setAuthCookies(response, result.accessToken(), result.rawRefreshToken(),
            result.refreshTokenExpiry());

        return ResponseEntity.ok(buildAuthResponse(result.accessToken(), result.rawRefreshToken(),
            result.user(), result.permissions()));
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request,
                                      HttpServletResponse response) {
        String tenantId = "tnt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantName = request.email().split("@")[0] + "-workspace";
        Tenant tenant = new Tenant(tenantId, tenantName, Map.of(), Tenant.Status.ACTIVE,
            Instant.now(), Instant.now());
        tenantRepository.save(tenant);

        var adminRole = roleService.createRole(tenantId, "Administrator",
            List.of(
                "runs:read", "runs:write", "spans:read", "evals:read", "evals:write",
                "alerts:read", "alerts:write", "dashboards:read", "dashboards:write",
                "users:read", "users:write", "settings:read", "settings:write",
                "audit:read", "export:read", "export:write", "admin"
            ),
            "Full administrative access");

        User user;
        try {
            user = userService.createUser(tenantId, request.email(), request.password(), request.displayName());
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of("error",
                "An account with this email already exists"));
        }

        userService.assignRole(user.userId(), adminRole.roleId());

        // Issue tokens directly from the created user — no second BCrypt round-trip
        var result = authenticationService.issueTokens(user);
        setAuthCookies(response, result.accessToken(), result.rawRefreshToken(),
            result.refreshTokenExpiry());

        return ResponseEntity.status(201).body(buildAuthResponse(
            result.accessToken(), result.rawRefreshToken(), user, result.permissions()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request,
                                     @RequestBody(required = false) RefreshRequest body,
                                     HttpServletResponse response) {
        // Accept refresh token from JSON body (BFF pattern) or from cookie (direct browser calls)
        String rawRefresh = (body != null && body.refreshToken() != null)
            ? body.refreshToken()
            : extractCookie(request, REFRESH_COOKIE);
        if (rawRefresh == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No refresh token"));
        }
        var result = authenticationService.refresh(rawRefresh);
        if (result == null) {
            clearAuthCookies(response);
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token invalid or expired"));
        }
        setAuthCookies(response, result.accessToken(), result.rawRefreshToken(),
            result.refreshTokenExpiry());
        return ResponseEntity.ok(buildAuthResponse(result.accessToken(), result.rawRefreshToken(),
            result.user(), result.permissions()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request,
                                    @RequestBody(required = false) RefreshRequest body,
                                    HttpServletResponse response) {
        // Revoke the current access token JTI
        String jti = (String) request.getAttribute("jti");
        String tenantId = TenantContext.getTenantIdOrNull();
        if (jti != null && tenantId != null) {
            blacklist.revoke(jti, tenantId, Instant.now().plus(jwtTokenService.getExpiry()));
        }

        // Revoke the refresh token so it cannot be used after logout
        String rawRefresh = (body != null && body.refreshToken() != null)
            ? body.refreshToken()
            : extractCookie(request, REFRESH_COOKIE);
        if (rawRefresh != null) {
            refreshTokenRepository.revoke(sha256(rawRefresh));
        }

        clearAuthCookies(response);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
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
            "displayName", user.displayName() != null ? user.displayName() : "",
            "tenantId", user.tenantId(),
            "permissions", permissions
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        // Look up user by email without credential check to generate reset token
        userService.findByEmail(request.tenantId(), request.email()).ifPresent(user -> {
            String rawToken = UUID.randomUUID().toString().replace("-", "") +
                              UUID.randomUUID().toString().replace("-", "");
            String tokenHash = sha256(rawToken);
            Instant expiry = Instant.now().plusSeconds(3600);
            passwordResetRepository.save(tokenHash, user.userId(), user.tenantId(), expiry);
            LOG.info("Password reset requested for tenant={}", request.tenantId());
            // In production: send email with rawToken link. Not implemented here.
        });
        // Always return success to prevent user enumeration
        return ResponseEntity.ok(Map.of("message",
            "If that email is registered, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        String tokenHash = sha256(request.token());
        var recordOpt = passwordResetRepository.findByHash(tokenHash);
        if (recordOpt.isEmpty() || !recordOpt.get().isValid()) {
            return ResponseEntity.status(400).body(Map.of("error", "Reset link is invalid or expired"));
        }
        var record = recordOpt.get();
        userService.updatePassword(record.userId(), request.newPassword());
        passwordResetRepository.markUsed(tokenHash);
        LOG.info("Password reset completed for tenant={}", record.tenantId());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response, String accessToken,
                                 String rawRefresh, Instant refreshExpiry) {
        response.addHeader("Set-Cookie", buildCookie(SESSION_COOKIE, accessToken, ACCESS_MAX_AGE));
        long refreshMaxAge = Math.max(0, refreshExpiry.getEpochSecond() - Instant.now().getEpochSecond());
        response.addHeader("Set-Cookie", buildCookie(REFRESH_COOKIE, rawRefresh, (int) refreshMaxAge));
    }

    private void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie(SESSION_COOKIE, "", 0));
        response.addHeader("Set-Cookie", buildCookie(REFRESH_COOKIE, "", 0));
    }

    private String buildCookie(String name, String value, int maxAge) {
        return name + "=" + value + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge;
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(c -> name.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> buildAuthResponse(String accessToken, String rawRefresh,
                                                   User user, java.util.Set<String> permissions) {
        return Map.of(
            "token", accessToken,
            "refreshToken", rawRefresh,
            "userId", user.userId(),
            "email", user.email(),
            "displayName", user.displayName() != null ? user.displayName() : "",
            "tenantId", user.tenantId(),
            "permissions", permissions
        );
    }

    private static String sha256(String input) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Request records ────────────────────────────────────────────────────────

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

    public record ForgotPasswordRequest(
        @NotBlank String tenantId,
        @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}

    public record RefreshRequest(@Nullable String refreshToken) {}
}
