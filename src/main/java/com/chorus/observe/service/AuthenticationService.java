package com.chorus.observe.service;

import com.chorus.observe.model.ApiKey;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.ApiKeyRepository;
import com.chorus.observe.persistence.RefreshTokenRepository;
import com.chorus.observe.security.JwtTokenService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AuthenticationService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserService userService;
    private final ApiKeyRepository apiKeyRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    /** Refresh token TTL in days. */
    private final long refreshTokenDays;

    public AuthenticationService(@NonNull UserService userService, @NonNull ApiKeyRepository apiKeyRepository,
                                 @NonNull JwtTokenService jwtTokenService,
                                 @NonNull RefreshTokenRepository refreshTokenRepository,
                                 @NonNull PasswordEncoder passwordEncoder,
                                 long refreshTokenDays) {
        this.userService = Objects.requireNonNull(userService);
        this.apiKeyRepository = Objects.requireNonNull(apiKeyRepository);
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService);
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.refreshTokenDays = refreshTokenDays;
    }

    public @Nullable LoginResult login(@NonNull String tenantId, @NonNull String email, @NonNull String password) {
        Optional<User> userOpt = userService.authenticate(tenantId, email, password);
        if (userOpt.isEmpty()) return null;
        User user = userOpt.get();

        userService.updateLastLoginAt(user.userId());
        LOG.info("Login successful for tenant={}", tenantId);

        return issueTokens(user);
    }

    /**
     * Issue access + refresh tokens for an already-authenticated user.
     * Used after registration (avoids a second BCrypt round-trip).
     */
    public @NonNull LoginResult issueTokens(@NonNull User user) {
        Set<String> permissions = Set.copyOf(userService.getUserPermissions(user.userId()));
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtTokenService.generateWithJti(user.tenantId(), user.userId(), permissions, jti);

        String rawRefresh = UUID.randomUUID().toString().replace("-", "") +
                            UUID.randomUUID().toString().replace("-", "");
        String refreshHash = sha256(rawRefresh);
        Instant refreshExpiry = Instant.now().plusSeconds(refreshTokenDays * 86400);
        refreshTokenRepository.save(refreshHash, user.userId(), user.tenantId(), jti, refreshExpiry);

        return new LoginResult(accessToken, rawRefresh, refreshExpiry, user, permissions);
    }

    /**
     * Validates a raw refresh token, rotates it, and issues a new access + refresh token pair.
     * Returns null if the token is invalid, expired, or revoked.
     */
    public @Nullable LoginResult refresh(@NonNull String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        Optional<RefreshTokenRepository.RefreshTokenRecord> recordOpt = refreshTokenRepository.findByHash(hash);
        if (recordOpt.isEmpty() || !recordOpt.get().isValid()) return null;

        RefreshTokenRepository.RefreshTokenRecord record = recordOpt.get();
        refreshTokenRepository.revoke(hash); // rotate: revoke old token

        Optional<User> userOpt = userService.getUser(record.userId());
        if (userOpt.isEmpty() || userOpt.get().status() != com.chorus.observe.model.User.Status.ACTIVE) {
            return null;
        }

        return issueTokens(userOpt.get());
    }

    public @NonNull ApiKey createApiKey(@NonNull String tenantId, @Nullable String userId, @NonNull String name,
                                        @NonNull Set<String> scopes, @Nullable Instant expiresAt) {
        String rawKey = "cko_" + UUID.randomUUID().toString().replace("-", "");
        String keyHash = sha256(rawKey);
        ApiKey apiKey = new ApiKey(keyHash, tenantId, userId, name, List.copyOf(scopes), expiresAt, null, Instant.now(), null);
        apiKeyRepository.save(apiKey);
        LOG.info("Created API key for tenant {}: {}", tenantId, name);
        return apiKey;
    }

    public boolean verifyKey(@NonNull String rawKey, @NonNull String keyHash) {
        return sha256(rawKey).equals(keyHash);
    }

    private static @NonNull String sha256(@NonNull String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static @NonNull String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public record LoginResult(
        @NonNull String accessToken,
        @NonNull String rawRefreshToken,
        @NonNull Instant refreshTokenExpiry,
        @NonNull User user,
        @NonNull Set<String> permissions
    ) {}
}
