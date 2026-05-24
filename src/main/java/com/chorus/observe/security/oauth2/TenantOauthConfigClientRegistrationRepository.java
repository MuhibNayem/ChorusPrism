package com.chorus.observe.security.oauth2;

import com.chorus.observe.model.TenantOauthConfig;
import com.chorus.observe.persistence.TenantOauthConfigRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

public class TenantOauthConfigClientRegistrationRepository implements ClientRegistrationRepository {

    private final TenantOauthConfigRepository repository;

    public TenantOauthConfigClientRegistrationRepository(@NonNull TenantOauthConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public ClientRegistration findByRegistrationId(@NonNull String registrationId) {
        int sep = registrationId.indexOf("__");
        if (sep < 0) {
            return null;
        }
        String tenantId = registrationId.substring(0, sep);
        String providerName = registrationId.substring(sep + 2);

        return repository.findByTenantIdAndProviderName(tenantId, providerName)
            .map(this::toClientRegistration)
            .orElse(null);
    }

    private ClientRegistration toClientRegistration(@NonNull TenantOauthConfig config) {
        String registrationId = config.tenantId() + "__" + config.providerName();

        return ClientRegistration.withRegistrationId(registrationId)
            .clientId(config.clientId())
            .clientSecret(config.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope(config.scopes().toArray(new String[0]))
            .issuerUri(config.issuerUri())
            .build();
    }
}
