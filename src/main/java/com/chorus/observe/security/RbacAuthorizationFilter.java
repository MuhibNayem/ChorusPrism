package com.chorus.observe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Enforces RBAC permissions on API endpoints.
 * <p>
 * Expects {@code X-Required-Permission} header (set by controller annotations via interceptor)
 * or checks the request attribute {@code scopes} populated by {@link JwtAuthFilter}.
 */
public class RbacAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RbacAuthorizationFilter.class);

    private final boolean enabled;

    public RbacAuthorizationFilter(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !TenantContext.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String required = request.getHeader("X-Required-Permission");
        if (required == null || required.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        @SuppressWarnings("unchecked")
        Set<String> scopes = (Set<String>) request.getAttribute("scopes");
        if (scopes == null) {
            scopes = Set.of();
        }

        if (!scopes.contains(required) && !scopes.contains(Permission.ADMIN)) {
            LOG.warn("RBAC denied: required={}, scopes={}", required, scopes);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permissions: " + required);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
