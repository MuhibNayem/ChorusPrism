package com.chorus.observe.security.saml2;

import com.chorus.observe.model.TenantSamlConfig;
import com.chorus.observe.persistence.TenantSamlConfigRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class TenantSamlConfigRelyingPartyRegistrationRepository implements RelyingPartyRegistrationRepository {

    private final TenantSamlConfigRepository configRepository;
    private final MetadataResolver metadataResolver;
    private final SpKeyService spKeyService;

    public TenantSamlConfigRelyingPartyRegistrationRepository(
            @NonNull TenantSamlConfigRepository configRepository,
            @NonNull MetadataResolver metadataResolver,
            @NonNull SpKeyService spKeyService) {
        this.configRepository = configRepository;
        this.metadataResolver = metadataResolver;
        this.spKeyService = spKeyService;
    }

    @Override
    public RelyingPartyRegistration findByRegistrationId(@NonNull String registrationId) {
        int sep = registrationId.indexOf("__");
        if (sep < 0) return null;
        String tenantId = registrationId.substring(0, sep);
        String providerName = registrationId.substring(sep + 2);

        return configRepository.findByTenantIdAndProviderName(tenantId, providerName)
            .filter(TenantSamlConfig::enabled)
            .map(config -> toRelyingPartyRegistration(config, tenantId))
            .orElse(null);
    }

    private RelyingPartyRegistration toRelyingPartyRegistration(@NonNull TenantSamlConfig config,
                                                                  @NonNull String tenantId) {
        String registrationId = config.tenantId() + "__" + config.providerName();

        // Resolve IdP verification cert: prefer direct PEM, then XML upload, then metadata URL
        String idpCertPem = resolveIdpCert(config);
        if (idpCertPem == null) {
            throw new IllegalStateException(
                "Could not resolve SAML signing certificate for " + registrationId +
                ". Set idpCertPem, upload metadata XML, or provide a valid metadata URL + thumbprint.");
        }

        X509Certificate idpCert = loadCertificate(idpCertPem);
        Saml2X509Credential verificationCredential = Saml2X509Credential.verification(idpCert);

        // SP signing/decryption credentials from persistent per-tenant key
        SpKeyService.SpKeyPair spKeys = spKeyService.getOrGenerate(tenantId);
        Saml2X509Credential signingCredential = Saml2X509Credential.signing(
            spKeys.privateKey(), spKeys.certificate());
        Saml2X509Credential decryptionCredential = Saml2X509Credential.decryption(
            spKeys.privateKey(), spKeys.certificate());

        var assertingPartyDetails = new RelyingPartyRegistration.AssertingPartyDetails.Builder()
            .entityId(config.entityId())
            .singleSignOnServiceLocation(config.signOnUrl())
            .singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT)
            .verificationX509Credentials(c -> c.add(verificationCredential))
            .wantAuthnRequestsSigned(true)
            .build();

        return RelyingPartyRegistration.withAssertingPartyMetadata(assertingPartyDetails)
            .registrationId(registrationId)
            .entityId("{baseUrl}/saml2/service-provider-metadata/" + registrationId)
            .assertionConsumerServiceLocation(config.acsUrl())
            .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
            .signingX509Credentials(c -> c.add(signingCredential))
            .decryptionX509Credentials(c -> c.add(decryptionCredential))
            .build();
    }

    private String resolveIdpCert(@NonNull TenantSamlConfig config) {
        // 1. Direct PEM supplied by admin
        if (config.idpCertPem() != null && !config.idpCertPem().isBlank()) {
            return config.idpCertPem();
        }
        // 2. Parse from uploaded metadata XML
        if (config.idpMetadataXml() != null && !config.idpMetadataXml().isBlank()) {
            String pem = metadataResolver.resolveCertificatePemFromXml(
                config.idpMetadataXml(), config.signingCertThumbprint());
            if (pem != null) return pem;
        }
        // 3. Fetch from metadata URL and match by thumbprint
        if (config.metadataUrl() != null && !config.metadataUrl().isBlank()
                && !config.signingCertThumbprint().isBlank()) {
            return metadataResolver.resolveCertificatePem(
                config.metadataUrl(), config.signingCertThumbprint());
        }
        return null;
    }

    private X509Certificate loadCertificate(@NonNull String pem) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load IdP certificate", e);
        }
    }
}
