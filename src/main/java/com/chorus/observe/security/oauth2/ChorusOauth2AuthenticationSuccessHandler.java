package com.chorus.observe.security.oauth2;

import com.chorus.observe.model.TenantOauthConfig;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.TenantOauthConfigRepository;
import com.chorus.observe.security.JwtTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.List;

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
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid registration ID");
            return;
        }
        String tenantId = parts[0];
        String providerName = parts[1];

        OAuth2User oauth2User = oauthToken.getPrincipal();
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not provided by IdP");
            return;
        }

        String defaultRole = configRepository.findByTenantIdAndProviderName(tenantId, providerName)
            .map(TenantOauthConfig::defaultRole)
            .orElse("VIEWER");

        User user = jitProvisioningService.provisionOrLink(
            tenantId, email, name, User.AuthSource.OAUTH2, defaultRole);

        String chorusJwt = jwtTokenService.generate(
            tenantId, user.userId(), java.util.Set.of());

        // Pass token in URL fragment (not sent to server) to prevent leakage in
        // referrer headers, server access logs, and browser history sync.
        String redirect = frontendRedirectUrl + "#token=" + chorusJwt;
        response.sendRedirect(redirect);
    }
}
