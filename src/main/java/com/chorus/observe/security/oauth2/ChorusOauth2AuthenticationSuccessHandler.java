package com.chorus.observe.security.oauth2;

import com.chorus.observe.model.RoleMapping;
import com.chorus.observe.model.TenantOauthConfig;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.TenantOauthConfigRepository;
import com.chorus.observe.security.JwtTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChorusOauth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JitProvisioningService jitProvisioningService;
    private final JwtTokenService jwtTokenService;
    private final TenantOauthConfigRepository configRepository;
    private final String frontendRedirectUrl;

    public ChorusOauth2AuthenticationSuccessHandler(
            @NonNull JitProvisioningService jitProvisioningService,
            @NonNull JwtTokenService jwtTokenService,
            @NonNull TenantOauthConfigRepository configRepository,
            @NonNull String frontendRedirectUrl) {
        this.jitProvisioningService = jitProvisioningService;
        this.jwtTokenService = jwtTokenService;
        this.configRepository = configRepository;
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication)
            throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid OAuth2 authentication");
            return;
        }

        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        String[] parts = registrationId.split("__", 2);
        if (parts.length != 2) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid registration ID format");
            return;
        }
        String tenantId = parts[0];
        String providerName = parts[1];

        TenantOauthConfig config = configRepository.findByTenantIdAndProviderName(tenantId, providerName)
            .orElse(null);

        Map<String, String> attrMap = config != null ? config.attributeMappings() : Map.of();

        OAuth2User oauth2User = oauthToken.getPrincipal();
        Map<String, Object> userAttrs = oauth2User.getAttributes();

        // Configurable claim names — defaults match standard OIDC claims
        String emailClaim = attrMap.getOrDefault("email", "email");
        String nameClaim = attrMap.getOrDefault("name", "name");
        String groupsClaim = attrMap.getOrDefault("groups", "groups");

        String email = getStringAttr(userAttrs, emailClaim);
        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Email claim '" + emailClaim + "' not provided by IdP");
            return;
        }

        // Enforce allowed email domains
        if (config != null && !config.allowedDomains().isEmpty()) {
            String domain = email.contains("@") ? email.substring(email.lastIndexOf('@') + 1) : "";
            if (config.allowedDomains().stream().noneMatch(d -> d.equalsIgnoreCase(domain))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Email domain '" + domain + "' is not allowed for this tenant");
                return;
            }
        }

        String displayName = getStringAttr(userAttrs, nameClaim);
        if (displayName == null || displayName.isBlank()) displayName = email;

        // Role mapping: evaluate groups claim against configured mappings
        List<String> groups = getListAttr(userAttrs, groupsClaim);
        String role = resolveRole(groups, config);

        User user = jitProvisioningService.provisionOrLink(
            tenantId, email, displayName, User.AuthSource.OAUTH2, role);

        String chorusJwt = jwtTokenService.generate(tenantId, user.userId(), Set.of());
        // Pass token in URL fragment to prevent leakage in referrer headers and server logs.
        response.sendRedirect(frontendRedirectUrl + "#token=" + chorusJwt);
    }

    private @Nullable String getStringAttr(@NonNull Map<String, Object> attrs, @NonNull String key) {
        Object val = attrs.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private @NonNull List<String> getListAttr(@NonNull Map<String, Object> attrs, @NonNull String key) {
        Object val = attrs.get(key);
        if (val instanceof Collection<?> col) {
            return col.stream().map(Object::toString).toList();
        }
        if (val instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    private @NonNull String resolveRole(@NonNull List<String> groups, @Nullable TenantOauthConfig config) {
        if (config == null) return "VIEWER";
        if (config.roleMappings().isEmpty()) return config.defaultRole();
        for (RoleMapping mapping : config.roleMappings()) {
            if (groups.contains(mapping.value())) return mapping.role();
        }
        return config.defaultRole();
    }
}
