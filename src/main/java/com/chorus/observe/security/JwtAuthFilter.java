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

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
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
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
