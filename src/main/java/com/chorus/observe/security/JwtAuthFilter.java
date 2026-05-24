package com.chorus.observe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Extracts and validates JWT Bearer tokens, populating {@link TenantContext}.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtTokenService jwtTokenService;
    private final boolean enabled;

    public JwtAuthFilter(@NonNull JwtTokenService jwtTokenService, boolean enabled) {
        this.jwtTokenService = jwtTokenService;
        this.enabled = enabled;
    }

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/actuator/health", "/actuator/info", "/actuator/prometheus", "/actuator/metrics",
        "/v3/api-docs", "/swagger-ui", "/swagger-ui.html", "/webjars/",
        "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password", "/api/v1/auth/verify-email"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            JwtTokenService.TokenClaims claims = jwtTokenService.parse(token);
            if (claims != null) {
                TenantContext.set(claims.tenantId(), claims.userId(), null);
                request.setAttribute("scopes", claims.scopes());

                var authorities = claims.scopes().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
                var authToken = new UsernamePasswordAuthenticationToken(
                    claims.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }
}
