package com.chorus.observe.security.saml2;

import com.chorus.observe.export.CredentialEncryptionService;
import com.chorus.observe.model.SsoSpKey;
import com.chorus.observe.persistence.SsoSpKeyRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-tenant SAML SP signing key pairs stored persistently in the database.
 * Keys are encrypted at rest using AES-256-GCM and cached in memory after first load.
 * Replaces the ephemeral {@code SamlSpKeyPair} singleton.
 */
public class SpKeyService {

    private static final Logger LOG = LoggerFactory.getLogger(SpKeyService.class);
    private static final int KEY_SIZE_BITS = 2048;
    private static final long CERT_VALIDITY_SECONDS = 10L * 365 * 24 * 3600; // 10 years

    private final SsoSpKeyRepository repository;
    private final CredentialEncryptionService encryptionService;
    private final Map<String, SpKeyPair> cache = new ConcurrentHashMap<>();

    public SpKeyService(@NonNull SsoSpKeyRepository repository,
                        @NonNull CredentialEncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    public record SpKeyPair(@NonNull RSAPrivateKey privateKey, @NonNull X509Certificate certificate) {}

    public @NonNull SpKeyPair getOrGenerate(@NonNull String tenantId) {
        SpKeyPair cached = cache.get(tenantId);
        if (cached != null) return cached;

        SpKeyPair loaded = repository.findByTenantId(tenantId)
            .map(this::deserialize)
            .orElseGet(() -> generate(tenantId));

        cache.put(tenantId, loaded);
        return loaded;
    }

    /** Force regeneration of the SP key pair for a tenant (e.g., after key rotation). */
    public @NonNull SpKeyPair rotate(@NonNull String tenantId) {
        cache.remove(tenantId);
        SpKeyPair fresh = generate(tenantId);
        cache.put(tenantId, fresh);
        return fresh;
    }

    private @NonNull SpKeyPair generate(@NonNull String tenantId) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(KEY_SIZE_BITS);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            X500Name subject = new X500Name("CN=Chorus SP, O=Chorus Observe, C=US");
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date notBefore = new Date();
            Date notAfter = new Date(notBefore.getTime() + CERT_VALIDITY_SECONDS * 1000L);

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());

            X509Certificate cert = new JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer));

            String encryptedKey = encryptionService.encrypt(
                Base64.getEncoder().encodeToString(privateKey.getEncoded()));
            String certPem = toPem(cert.getEncoded());

            SsoSpKey record = new SsoSpKey(null, tenantId, encryptedKey, certPem,
                "RSA", KEY_SIZE_BITS, Instant.now());
            repository.save(record);

            LOG.info("Generated new SP signing key for tenant={}", tenantId);
            return new SpKeyPair(privateKey, cert);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate SP signing key for tenant " + tenantId, e);
        }
    }

    private @NonNull SpKeyPair deserialize(@NonNull SsoSpKey record) {
        try {
            String pkcs8Base64 = encryptionService.decrypt(record.privateKeyEncrypted());
            byte[] pkcs8Bytes = Base64.getDecoder().decode(pkcs8Base64);
            RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(pkcs8Bytes));

            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(
                    fromPem(record.certPem())));

            return new SpKeyPair(privateKey, cert);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load SP signing key for tenant " + record.tenantId(), e);
        }
    }

    private static @NonNull String toPem(byte[] derBytes) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(derBytes);
        return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----";
    }

    private static byte[] fromPem(@NonNull String pem) {
        String stripped = pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(stripped);
    }
}
