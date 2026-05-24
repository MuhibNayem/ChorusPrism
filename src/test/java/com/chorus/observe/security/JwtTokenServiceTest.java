package com.chorus.observe.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String TEST_SECRET = "this-is-a-very-long-test-secret-key-32-chars";
    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(TEST_SECRET, Duration.ofMinutes(60));
    }

    @Test
    void shouldGenerateNonNullToken() {
        String token = tokenService.generate("tenant-1", "user-1", Set.of("runs:read"));
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void shouldParseValidToken() {
        String token = tokenService.generate("tenant-1", "user-1", Set.of("runs:read", "admin"));
        JwtTokenService.TokenClaims claims = tokenService.parse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.userId()).isEqualTo("user-1");
        assertThat(claims.tenantId()).isEqualTo("tenant-1");
        assertThat(claims.scopes()).containsExactlyInAnyOrder("runs:read", "admin");
        assertThat(claims.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void shouldReturnNullForExpiredToken() {
        JwtTokenService shortLived = new JwtTokenService(TEST_SECRET, Duration.ofMillis(1));
        String token = shortLived.generate("tenant-1", "user-1", Set.of("runs:read"));

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        JwtTokenService.TokenClaims claims = tokenService.parse(token);
        assertThat(claims).isNull();
    }

    @Test
    void shouldReturnNullForInvalidSignature() {
        String token = tokenService.generate("tenant-1", "user-1", Set.of("runs:read"));
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "tampered";

        JwtTokenService.TokenClaims claims = tokenService.parse(tampered);
        assertThat(claims).isNull();
    }

    @Test
    void shouldReturnNullForMalformedToken() {
        JwtTokenService.TokenClaims claims = tokenService.parse("not-a-jwt");
        assertThat(claims).isNull();
    }
}
