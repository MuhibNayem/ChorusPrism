package com.chorus.observe.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * JWT token generation and validation.
 * <p>
 * Uses HS256 with a configurable secret. Each token has a unique JTI (JWT ID)
 * for revocation support. Scopes are stored as a JSON array (RFC 8693 compliant).
 */
public final class JwtTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenService.class);
    private static final String ISSUER = "chorus-observe";

    private final SecretKey key;
    private final Duration expiry;

    public JwtTokenService(@NonNull String secret, @NonNull Duration expiry) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiry = Objects.requireNonNull(expiry);
    }

    public @NonNull String generate(@NonNull String tenantId, @NonNull String userId, @NonNull Set<String> scopes) {
        return generateWithJti(tenantId, userId, scopes, UUID.randomUUID().toString());
    }

    public @NonNull String generateWithJti(@NonNull String tenantId, @NonNull String userId,
                                            @NonNull Set<String> scopes, @NonNull String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(ISSUER)
            .subject(userId)
            .id(jti)
            .claim("tenant_id", tenantId)
            .claim("scope", List.copyOf(scopes))  // RFC 8693: JSON array
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiry)))
            .signWith(key)
            .compact();
    }

    public @Nullable TokenClaims parse(@NonNull String token) {
        try {
            var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String tenantId = claims.get("tenant_id", String.class);
            String jti = claims.getId();

            @SuppressWarnings("unchecked")
            Object scopeRaw = claims.get("scope");
            Set<String> scopes;
            if (scopeRaw instanceof List<?> list) {
                scopes = list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(java.util.stream.Collectors.toSet());
            } else if (scopeRaw instanceof String s) {
                // backward-compatible: parse comma-separated legacy tokens
                scopes = s.isBlank() ? Set.of() : Set.of(s.split(","));
            } else {
                scopes = Set.of();
            }

            return new TokenClaims(claims.getSubject(), tenantId, jti, scopes,
                claims.getExpiration().toInstant());
        } catch (Exception e) {
            LOG.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    public @NonNull Duration getExpiry() { return expiry; }

    public record TokenClaims(
        @NonNull String userId,
        @NonNull String tenantId,
        @Nullable String jti,
        @NonNull Set<String> scopes,
        @NonNull Instant expiresAt
    ) {}
}
