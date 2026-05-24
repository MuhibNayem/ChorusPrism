package com.chorus.observe.security.scim;

import com.chorus.observe.model.ScimToken;
import com.chorus.observe.persistence.ScimTokenRepository;
import com.chorus.observe.security.TenantContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

public class ScimTokenAuthFilter implements Filter {

    private final ScimTokenRepository tokenRepository;

    public ScimTokenAuthFilter(@NonNull ScimTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        if (!path.startsWith("/scim/")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String header = httpRequest.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid SCIM bearer token");
                return;
            }

            String rawToken = header.substring(7);
            String tokenHash = hashToken(rawToken);

            ScimToken token = tokenRepository.findByTokenHash(tokenHash).orElse(null);
            if (token == null || !token.isActive()) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired SCIM token");
                return;
            }

            TenantContext.set(token.tenantId(), null, null);
            httpRequest.setAttribute("scopes", Set.copyOf(token.scopes()));

            var authorities = token.scopes().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
            var authToken = new UsernamePasswordAuthenticationToken(
                "scim:" + token.tenantId(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);

            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private @NonNull String hashToken(@NonNull String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    private @NonNull String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
