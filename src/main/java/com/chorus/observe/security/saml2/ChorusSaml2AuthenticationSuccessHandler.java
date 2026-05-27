package com.chorus.observe.security.saml2;

import com.chorus.observe.model.RoleMapping;
import com.chorus.observe.model.TenantSamlConfig;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.TenantSamlConfigRepository;
import com.chorus.observe.security.JwtTokenService;
import com.chorus.observe.security.oauth2.JitProvisioningService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChorusSaml2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JitProvisioningService jitProvisioningService;
    private final JwtTokenService jwtTokenService;
    private final AssertionIdCache assertionIdCache;
    private final TenantSamlConfigRepository configRepository;
    private final String frontendRedirectUrl;

    public ChorusSaml2AuthenticationSuccessHandler(
            @NonNull JitProvisioningService jitProvisioningService,
            @NonNull JwtTokenService jwtTokenService,
            @NonNull AssertionIdCache assertionIdCache,
            @NonNull TenantSamlConfigRepository configRepository,
            @NonNull String frontendRedirectUrl) {
        this.jitProvisioningService = jitProvisioningService;
        this.jwtTokenService = jwtTokenService;
        this.assertionIdCache = assertionIdCache;
        this.configRepository = configRepository;
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication)
            throws IOException, ServletException {

        if (!(authentication instanceof Saml2Authentication samlAuth)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid SAML authentication");
            return;
        }

        String assertionId = extractAssertionId(samlAuth);
        if (assertionId != null && assertionIdCache.isReplay(assertionId)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "SAML assertion replay detected");
            return;
        }

        String registrationId = extractRegistrationId(request);
        if (registrationId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not determine SAML registration");
            return;
        }

        String[] parts = registrationId.split("__", 2);
        if (parts.length != 2) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid registration ID format");
            return;
        }
        String tenantId = parts[0];
        String providerName = parts[1];

        TenantSamlConfig config = configRepository.findByTenantIdAndProviderName(tenantId, providerName)
            .orElse(null);

        Map<String, String> attrMap = config != null ? config.attributeMappings() : Map.of();

        // Extract attributes using configurable claim names
        Map<String, List<Object>> samlAttributes = extractSamlAttributes(samlAuth);
        String emailClaim = attrMap.getOrDefault("email",
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");
        String nameClaim = attrMap.getOrDefault("name",
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/displayname");
        String groupsClaim = attrMap.getOrDefault("groups", "groups");

        String email = extractAttribute(samlAttributes, emailClaim);
        if (email == null || email.isBlank()) {
            // Fall back to NameID
            email = samlAuth.getName();
        }
        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not provided by SAML IdP");
            return;
        }

        // Enforce allowed email domains
        if (config != null && !config.allowedDomains().isEmpty()) {
            String domain = email.contains("@") ? email.substring(email.lastIndexOf('@') + 1) : "";
            if (!isDomainAllowed(domain, config.allowedDomains())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Email domain '" + domain + "' is not allowed for this tenant");
                return;
            }
        }

        String displayName = extractAttribute(samlAttributes, nameClaim);
        if (displayName == null || displayName.isBlank()) {
            displayName = email;
        }

        // Determine role via role mappings, fall back to defaultRole
        List<Object> groups = samlAttributes.getOrDefault(groupsClaim, List.of());
        String role = resolveRole(groups, config, email);

        User user = jitProvisioningService.provisionOrLink(
            tenantId, email, displayName, User.AuthSource.SAML, role);

        String chorusJwt = jwtTokenService.generate(tenantId, user.userId(), Set.of());
        // Pass token in URL fragment to prevent leakage in referrer headers and server logs.
        response.sendRedirect(frontendRedirectUrl + "#token=" + chorusJwt);
    }

    private @Nullable String extractAttribute(@NonNull Map<String, List<Object>> attrs,
                                               @NonNull String claimName) {
        List<Object> values = attrs.get(claimName);
        if (values != null && !values.isEmpty()) {
            return values.getFirst().toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private @NonNull Map<String, List<Object>> extractSamlAttributes(@NonNull Saml2Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            return (Map<String, List<Object>>) map;
        }
        // Extract from SAML2 authenticated principal attributes
        if (auth.getPrincipal() instanceof org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal principal) {
            return principal.getAttributes();
        }
        return Map.of();
    }

    private @NonNull String resolveRole(@NonNull List<Object> groups,
                                         @Nullable TenantSamlConfig config,
                                         @NonNull String email) {
        if (config == null || config.roleMappings().isEmpty()) {
            return config != null ? config.defaultRole() : "VIEWER";
        }
        List<String> groupStrings = groups.stream().map(Object::toString).toList();
        for (RoleMapping mapping : config.roleMappings()) {
            if (groupStrings.contains(mapping.value())) {
                return mapping.role();
            }
        }
        return config.defaultRole();
    }

    private boolean isDomainAllowed(@NonNull String domain, @NonNull List<String> allowedDomains) {
        return allowedDomains.stream().anyMatch(d -> d.equalsIgnoreCase(domain));
    }

    private @Nullable String extractAssertionId(@NonNull Saml2Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof org.opensaml.saml.saml2.core.Assertion assertion) {
            return assertion.getID();
        }
        return null;
    }

    private @Nullable String extractRegistrationId(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/login/saml2/sso/";
        int idx = uri.indexOf(prefix);
        if (idx >= 0) {
            return uri.substring(idx + prefix.length());
        }
        return null;
    }
}
