package com.chorus.observe.security.saml2;

import com.chorus.observe.model.TenantSamlConfig;
import com.chorus.observe.persistence.TenantSamlConfigRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;

public class TenantSamlConfigRelyingPartyRegistrationRepository implements RelyingPartyRegistrationRepository {

    private final TenantSamlConfigRepository configRepository;
    private final MetadataResolver metadataResolver;

    public TenantSamlConfigRelyingPartyRegistrationRepository(
            @NonNull TenantSamlConfigRepository configRepository,
            @NonNull MetadataResolver metadataResolver) {
        this.configRepository = configRepository;
        this.metadataResolver = metadataResolver;
    }

    @Override
    public RelyingPartyRegistration findByRegistrationId(@NonNull String registrationId) {
        int sep = registrationId.indexOf("__");
        if (sep < 0) {
            return null;
        }
        String tenantId = registrationId.substring(0, sep);
        String providerName = registrationId.substring(sep + 2);

        return configRepository.findByTenantIdAndProviderName(tenantId, providerName)
            .filter(TenantSamlConfig::enabled)
            .map(this::toRelyingPartyRegistration)
            .orElse(null);
    }

    private RelyingPartyRegistration toRelyingPartyRegistration(@NonNull TenantSamlConfig config) {
        String registrationId = config.tenantId() + "__" + config.providerName();

        String certPem = metadataResolver.resolveCertificatePem(
            config.metadataUrl(), config.signingCertThumbprint());

        if (certPem == null) {
            throw new IllegalStateException("Could not resolve SAML signing certificate for " + registrationId);
        }

        X509Certificate idpCertificate = loadCertificate(certPem);
        Saml2X509Credential verificationCredential = Saml2X509Credential.verification(idpCertificate);

        RSAPrivateKey privateKey = (RSAPrivateKey) SamlSpKeyPair.get().getPrivate();
        Saml2X509Credential signingCredential = Saml2X509Credential.signing(privateKey, idpCertificate);

        var assertingPartyDetails = new RelyingPartyRegistration.AssertingPartyDetails.Builder()
            .entityId(config.entityId())
            .singleSignOnServiceLocation(config.signOnUrl())
            .singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT)
            .verificationX509Credentials(c -> c.add(verificationCredential))
            .build();

        return RelyingPartyRegistration.withAssertingPartyMetadata(assertingPartyDetails)
            .registrationId(registrationId)
            .entityId(config.entityId())
            .assertionConsumerServiceLocation(config.acsUrl())
            .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
            .signingX509Credentials(c -> c.add(signingCredential))
            .decryptionX509Credentials(c -> c.add(signingCredential))
            .build();
    }

    private X509Certificate loadCertificate(@NonNull String pem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load certificate", e);
        }
    }
}
