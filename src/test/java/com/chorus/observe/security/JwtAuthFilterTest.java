package com.chorus.observe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private static final String TEST_SECRET = "this-is-a-very-long-test-secret-key-32-chars";
    private JwtTokenService tokenService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        tokenService = new JwtTokenService(TEST_SECRET, Duration.ofMinutes(60));
        filter = new JwtAuthFilter(tokenService, true);
    }

    @Test
    void shouldBypassPublicPathWithoutAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        request.setRequestURI("/actuator/health");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldContinueChainWhenTokenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        request.setRequestURI("/api/v1/runs");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSetSecurityContextForValidToken() throws ServletException, IOException {
        String token = tokenService.generate("tenant-1", "user-1", Set.of("runs:read", "admin"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> authName = new AtomicReference<>();
        AtomicReference<Set<String>> authAuthorities = new AtomicReference<>();
        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null) {
                    authName.set(auth.getName());
                    authAuthorities.set(auth.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(java.util.stream.Collectors.toSet()));
                }
            }
        };
        request.setRequestURI("/api/v1/runs");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, chain);

        assertThat(authName.get()).isEqualTo("user-1");
        assertThat(authAuthorities.get()).containsExactlyInAnyOrder("runs:read", "admin");
    }

    @Test
    void shouldContinueChainForInvalidToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        request.setRequestURI("/api/v1/runs");
        request.addHeader("Authorization", "Bearer invalid-token");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldClearSecurityContextAfterRequest() throws ServletException, IOException {
        String token = tokenService.generate("tenant-1", "user-1", Set.of("runs:read"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        request.setRequestURI("/api/v1/runs");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.isAuthenticated()).isFalse();
    }
}
