package com.chorus.observe.security;

import com.chorus.observe.persistence.TokenBlacklistRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * Extracts and validates JWT Bearer tokens (from Authorization header or
 * {@code chorus_session} httpOnly cookie), checks the JTI revocation list,
 * and populates {@link TenantContext}.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String SESSION_COOKIE = "chorus_session";

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistRepository blacklist;
    private final boolean enabled;

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/actuator/health", "/actuator/info", "/actuator/prometheus", "/actuator/metrics",
        "/v3/api-docs", "/swagger-ui", "/swagger-ui.html", "/webjars/",
        "/api/v1/auth/login", "/api/v1/auth/register",
        "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password", "/api/v1/auth/verify-email"
    );

    public JwtAuthFilter(@NonNull JwtTokenService jwtTokenService,
                         @NonNull TokenBlacklistRepository blacklist,
                         boolean enabled) {
        this.jwtTokenService = jwtTokenService;
        this.blacklist = blacklist;
        this.enabled = enabled;
    }

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

        String rawToken = extractToken(request);
        if (rawToken != null) {
            JwtTokenService.TokenClaims claims = jwtTokenService.parse(rawToken);
            if (claims != null && !isRevoked(claims)) {
                TenantContext.set(claims.tenantId(), claims.userId(), null);
                request.setAttribute("scopes", claims.scopes());
                request.setAttribute("jti", claims.jti());

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

    private @Nullable String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                .filter(c -> SESSION_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    private boolean isRevoked(JwtTokenService.TokenClaims claims) {
        if (claims.jti() == null) return false;
        try {
            return blacklist.isRevoked(claims.jti());
        } catch (Exception e) {
            LOG.warn("Could not check token revocation for jti={}: {}", claims.jti(), e.getMessage());
            return false;
        }
    }
}
