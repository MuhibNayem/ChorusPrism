package com.chorus.observe.security.saml2;

import com.chorus.observe.model.TenantSamlConfig;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.TenantSamlConfigRepository;
import com.chorus.observe.security.JwtTokenService;
import com.chorus.observe.security.oauth2.JitProvisioningService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
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
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid registration ID");
            return;
        }
        String tenantId = parts[0];
        String providerName = parts[1];

        Object principal = samlAuth.getPrincipal();
        String email = principal instanceof org.springframework.security.core.AuthenticatedPrincipal ap
            ? ap.getName()
            : principal.toString();

        String displayName = principal instanceof org.springframework.security.core.AuthenticatedPrincipal ap
            ? ap.getName()
            : principal.toString();

        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not provided by SAML IdP");
            return;
        }

        String defaultRole = configRepository.findByTenantIdAndProviderName(tenantId, providerName)
            .map(TenantSamlConfig::defaultRole)
            .orElse("VIEWER");

        User user = jitProvisioningService.provisionOrLink(
            tenantId, email, displayName, User.AuthSource.SAML, defaultRole);

        String chorusJwt = jwtTokenService.generate(
            tenantId, user.userId(), Set.of());
        // Pass token in URL fragment (not sent to server) to prevent leakage in
        // referrer headers, server access logs, and browser history sync.
        response.sendRedirect(frontendRedirectUrl + "#token=" + chorusJwt);
    }

    private String extractAssertionId(@NonNull Saml2Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof org.opensaml.saml.saml2.core.Assertion assertion) {
            return assertion.getID();
        }
        return null;
    }

    private String extractRegistrationId(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        // SAML2 SSO endpoint pattern: /login/saml2/sso/{registrationId}
        String prefix = "/login/saml2/sso/";
        int idx = uri.indexOf(prefix);
        if (idx >= 0) {
            return uri.substring(idx + prefix.length());
        }
        return null;
    }
}
