package com.chorus.observe.service;

import com.chorus.observe.model.ApiKey;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.ApiKeyRepository;
import com.chorus.observe.security.JwtTokenService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(@NonNull UserService userService, @NonNull ApiKeyRepository apiKeyRepository,
                                 @NonNull JwtTokenService jwtTokenService, @NonNull PasswordEncoder passwordEncoder) {
        this.userService = Objects.requireNonNull(userService);
        this.apiKeyRepository = Objects.requireNonNull(apiKeyRepository);
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    public @Nullable LoginResult login(@NonNull String tenantId, @NonNull String email, @NonNull String password) {
        Optional<User> userOpt = userService.authenticate(tenantId, email, password);
        if (userOpt.isEmpty()) return null;
        User user = userOpt.get();
        Set<String> permissions = Set.copyOf(userService.getUserPermissions(user.userId()));
        String token = jwtTokenService.generate(tenantId, user.userId(), permissions);
        LOG.info("User {} logged in to tenant {}", email, tenantId);
        return new LoginResult(token, user, permissions);
    }

    public @NonNull ApiKey createApiKey(@NonNull String tenantId, @Nullable String userId, @NonNull String name,
                                        @NonNull Set<String> scopes, @Nullable Instant expiresAt) {
        String rawKey = "cko_" + UUID.randomUUID().toString().replace("-", "");
        String keyHash = passwordEncoder.encode(rawKey);
        ApiKey apiKey = new ApiKey(keyHash, tenantId, userId, name, List.copyOf(scopes), expiresAt, null, Instant.now(), null);
        apiKeyRepository.save(apiKey);
        LOG.info("Created API key for tenant {}: {}", tenantId, name);
        return apiKey;
    }

    public boolean verifyKey(@NonNull String rawKey, @NonNull String keyHash) {
        return passwordEncoder.matches(rawKey, keyHash);
    }

    public record LoginResult(@NonNull String token, @NonNull User user, @NonNull Set<String> permissions) {}
}
