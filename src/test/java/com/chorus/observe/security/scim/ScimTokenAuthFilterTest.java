package com.chorus.observe.security.scim;

import com.chorus.observe.model.ScimToken;
import com.chorus.observe.persistence.InMemoryScimTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScimTokenAuthFilterTest {

    private InMemoryScimTokenRepository tokenRepository;
    private ScimTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        tokenRepository = new InMemoryScimTokenRepository();
        filter = new ScimTokenAuthFilter(tokenRepository);
    }

    @Test
    void shouldAuthenticateWithValidToken() throws ServletException, IOException {
        String rawToken = "test-token-123";
        String tokenHash = sha256(rawToken);
        tokenRepository.save(new ScimToken(null, "tenant-1", "test", tokenHash,
            List.of("scim:users:read"), Instant.now(), null, null));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/scim/v2/Users");
        request.addHeader("Authorization", "Bearer " + rawToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.wasCalled).isTrue();
    }

    @Test
    void shouldRejectMissingHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/scim/v2/Users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.wasCalled).isFalse();
    }

    @Test
    void shouldRejectInvalidToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/scim/v2/Users");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.wasCalled).isFalse();
    }

    @Test
    void shouldRejectExpiredToken() throws ServletException, IOException {
        String rawToken = "expired-token";
        String tokenHash = sha256(rawToken);
        tokenRepository.save(new ScimToken(null, "tenant-1", "test", tokenHash,
            List.of("scim:users:read"), Instant.now(), Instant.now().minus(1, ChronoUnit.HOURS), null));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/scim/v2/Users");
        request.addHeader("Authorization", "Bearer " + rawToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.wasCalled).isFalse();
    }

    @Test
    void shouldBypassNonScimPaths() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.wasCalled).isTrue();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class CapturingFilterChain implements FilterChain {
        boolean wasCalled = false;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            wasCalled = true;
        }
    }
}
