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
import java.util.Objects;
import java.util.Set;

/**
 * JWT token generation and validation.
 * <p>
 * Uses HS256 with a configurable secret. Tokens include tenant_id, user_id, and scopes.
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
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(ISSUER)
            .subject(userId)
            .claim("tenant_id", tenantId)
            .claim("scopes", String.join(",", scopes))
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
            String scopesStr = claims.get("scopes", String.class);
            Set<String> scopes = scopesStr != null ? Set.of(scopesStr.split(",")) : Set.of();

            return new TokenClaims(
                claims.getSubject(),
                tenantId,
                scopes,
                claims.getExpiration().toInstant()
            );
        } catch (Exception e) {
            LOG.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    public record TokenClaims(@NonNull String userId, @NonNull String tenantId, @NonNull Set<String> scopes, @NonNull Instant expiresAt) {}
}
