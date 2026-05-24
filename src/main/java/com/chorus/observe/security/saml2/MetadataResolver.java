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

public class MetadataResolver {

    private final HttpClient httpClient;

    public MetadataResolver() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public @Nullable String resolveCertificatePem(@NonNull String metadataUrl,
                                                   @NonNull String expectedThumbprint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(metadataUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            String body = response.body();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<X509Certificate>([^<]+)</X509Certificate>");
            java.util.regex.Matcher matcher = pattern.matcher(body);

            while (matcher.find()) {
                String base64Cert = matcher.group(1).replaceAll("\\s", "");
                String thumbprint = computeThumbprint(base64Cert);
                if (thumbprint.equalsIgnoreCase(expectedThumbprint)) {
                    return "-----BEGIN CERTIFICATE-----\n"
                        + base64Cert.replaceAll("(.{64})", "$1\n")
                        + "\n-----END CERTIFICATE-----";
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private @NonNull String computeThumbprint(@NonNull String base64Cert) throws Exception {
        byte[] certBytes = Base64.getDecoder().decode(base64Cert);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) factory.generateCertificate(
            new java.io.ByteArrayInputStream(certBytes));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        return bytesToHex(digest);
    }

    private @NonNull String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
