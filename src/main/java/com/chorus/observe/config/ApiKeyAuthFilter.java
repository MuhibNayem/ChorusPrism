package com.chorus.observe.config;

import com.chorus.observe.model.ApiKey;
import com.chorus.observe.persistence.ApiKeyRepository;
import com.chorus.observe.security.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * API key authentication filter with database-backed validation.
 * <p>
 * Validates {@code X-API-Key} header against the {@code api_keys} table.
 * Supports tenant-scoped keys with scopes. Populates {@link TenantContext} on success.
 * <p>
 * Protects all endpoints except actuator health and OpenAPI docs.
 */
public class ApiKeyAuthFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/actuator/health", "/actuator/info", "/actuator/prometheus", "/actuator/metrics",
        "/v3/api-docs", "/swagger-ui", "/swagger-ui.html", "/webjars/",
        "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password", "/api/v1/auth/verify-email"
    );

    private final ApiKeyRepository apiKeyRepository;
    private final boolean enabled;

    public ApiKeyAuthFilter(@NonNull ApiKeyRepository apiKeyRepository, boolean enabled) {
        this.apiKeyRepository = apiKeyRepository;
        this.enabled = enabled;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (!enabled || isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = req.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<ApiKey> keyOpt = lookupKey(apiKey);
        if (keyOpt.isEmpty() || keyOpt.get().isRevoked() || keyOpt.get().isExpired()) {
            LOG.warn("Invalid or expired API key for request to {} from {}", path, req.getRemoteAddr());
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write(jsonError("Unauthorized: valid X-API-Key header required"));
            return;
        }

        ApiKey key = keyOpt.get();
        apiKeyRepository.updateLastUsed(key.keyHash(), Instant.now());
        TenantContext.set(key.tenantId(), key.userId(), key.keyHash());
        req.setAttribute("scopes", Set.copyOf(key.scopes()));

        var authorities = key.scopes().stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
        var authToken = new UsernamePasswordAuthenticationToken(
            key.userId() != null ? key.userId() : key.keyHash(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authToken);

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private Optional<ApiKey> lookupKey(@NonNull String rawKey) {
        // API keys are stored as SHA-256 hashes for fast deterministic lookup.
        String keyHash = sha256(rawKey);
        return apiKeyRepository.findByKeyHash(keyHash);
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
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean isPublic(@NonNull String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private @NonNull String jsonError(@NonNull String message) {
        return "{\"timestamp\":\"" + Instant.now() + "\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
    }
}
