package com.chorus.observe.security.saml2;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetadataResolver {

    private static final Pattern CERT_PATTERN = Pattern.compile(
        "<X509Certificate>([^<]+)</X509Certificate>");

    private final HttpClient httpClient;

    public MetadataResolver() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Resolves the IdP signing certificate PEM from a metadata URL, matching by SHA-256 thumbprint.
     * Returns null if the URL is unreachable or no certificate matches.
     */
    public @Nullable String resolveCertificatePem(@Nullable String metadataUrl,
                                                   @NonNull String expectedThumbprint) {
        if (metadataUrl == null || metadataUrl.isBlank()) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(metadataUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            return extractCertFromXml(response.body(), expectedThumbprint);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves the IdP signing certificate PEM from an already-fetched metadata XML string.
     * If {@code expectedThumbprint} is blank or empty, the first certificate in the metadata is returned.
     */
    public @Nullable String resolveCertificatePemFromXml(@NonNull String metadataXml,
                                                          @Nullable String expectedThumbprint) {
        if (metadataXml.isBlank()) return null;
        if (expectedThumbprint == null || expectedThumbprint.isBlank()) {
            return extractFirstCert(metadataXml);
        }
        return extractCertFromXml(metadataXml, expectedThumbprint);
    }

    private @Nullable String extractCertFromXml(@NonNull String xml, @NonNull String expectedThumbprint) {
        Matcher matcher = CERT_PATTERN.matcher(xml);
        while (matcher.find()) {
            String base64Cert = matcher.group(1).replaceAll("\\s", "");
            try {
                String thumbprint = computeThumbprint(base64Cert);
                if (thumbprint.equalsIgnoreCase(expectedThumbprint)) {
                    return toPem(base64Cert);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private @Nullable String extractFirstCert(@NonNull String xml) {
        Matcher matcher = CERT_PATTERN.matcher(xml);
        if (matcher.find()) {
            String base64Cert = matcher.group(1).replaceAll("\\s", "");
            return toPem(base64Cert);
        }
        return null;
    }

    private static @NonNull String toPem(@NonNull String base64Cert) {
        return "-----BEGIN CERTIFICATE-----\n"
            + base64Cert.replaceAll("(.{64})", "$1\n")
            + "\n-----END CERTIFICATE-----";
    }

    private @NonNull String computeThumbprint(@NonNull String base64Cert) throws Exception {
        byte[] certBytes = Base64.getDecoder().decode(base64Cert);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) factory.generateCertificate(
            new java.io.ByteArrayInputStream(certBytes));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        return bytesToHex(digest);
    }

    private static @NonNull String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
