package com.chorus.observe.notification;

import org.jspecify.annotations.NonNull;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * SSRF-preventing URL validator for outbound HTTP requests.
 * <p>
 * Blocks private IP ranges (IPv4 and IPv6), localhost, link-local addresses,
 * and cloud metadata endpoints. Used by {@link WebhookDispatcher},
 * {@link S3ExportClient}, and any other component that makes user-configurable
 * outbound HTTP calls.
 * <p>
 * Two entry-points are provided:
 * <ul>
 *   <li>{@link #validate(String)} — general validation; allows both http and https.</li>
 *   <li>{@link #validateWebhook(String)} — stricter; requires HTTPS for webhook URLs.</li>
 * </ul>
 */
public final class UrlValidator {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("file", "ftp", "gopher", "jar");

    /**
     * AWS IPv6 instance metadata endpoint (fd00:ec2::254 in expanded form).
     * Blocked in addition to the fc00::/7 and fe80::/10 private ranges.
     */
    private static final String AWS_IPV6_METADATA_HOST = "fd00:ec2::254";

    private UrlValidator() {}

    /**
     * Validate that a URL is safe for outbound HTTP/HTTPS requests.
     * Allows both {@code http} and {@code https} schemes.
     *
     * @param url the URL string to validate
     * @throws IllegalArgumentException if the URL is unsafe
     */
    public static void validate(@NonNull String url) {
        URI uri = parseUri(url);
        String scheme = validateScheme(uri, url, false);
        String host = validateHost(uri, url);
        resolveAndBlockPrivate(host, url);
    }

    /**
     * Validate that a webhook URL is safe and uses HTTPS only.
     * Calls {@link #validate(String)} then additionally blocks plain {@code http://}.
     *
     * @param url the webhook URL string to validate
     * @throws IllegalArgumentException if the URL is unsafe or not HTTPS
     */
    public static void validateWebhook(@NonNull String url) {
        URI uri = parseUri(url);
        String scheme = validateScheme(uri, url, true);
        String host = validateHost(uri, url);
        resolveAndBlockPrivate(host, url);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static URI parseUri(@NonNull String url) {
        try {
            return URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    /**
     * @param httpsOnly when {@code true} rejects http:// (for webhook usage)
     */
    private static String validateScheme(URI uri, String url, boolean httpsOnly) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL must have a scheme: " + url);
        }
        String lowerScheme = scheme.toLowerCase();
        if (BLOCKED_SCHEMES.contains(lowerScheme)) {
            throw new IllegalArgumentException("URL scheme not allowed: " + scheme);
        }
        if (httpsOnly && !"https".equals(lowerScheme)) {
            throw new IllegalArgumentException("Only HTTPS URLs are allowed for webhooks: " + url);
        }
        return lowerScheme;
    }

    private static String validateHost(URI uri, String url) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must have a host: " + url);
        }

        String lowerHost = host.toLowerCase();

        // Hostname-level checks (before DNS resolution)
        if (lowerHost.equals("localhost") || lowerHost.equals("127.0.0.1") || lowerHost.equals("::1")
            || lowerHost.endsWith(".localhost") || lowerHost.endsWith(".local")) {
            throw new IllegalArgumentException("Localhost URLs are not allowed: " + url);
        }

        // Block well-known metadata endpoints
        if (lowerHost.equals("169.254.169.254") || lowerHost.startsWith("169.254.")
            || lowerHost.equals("metadata.google.internal")
            || lowerHost.equals("metadata") || lowerHost.endsWith(".metadata")) {
            throw new IllegalArgumentException("Metadata endpoint URLs are not allowed: " + url);
        }

        // Block AWS IPv6 metadata endpoint by hostname
        if (lowerHost.equals(AWS_IPV6_METADATA_HOST)) {
            throw new IllegalArgumentException("AWS IPv6 metadata endpoint is not allowed: " + url);
        }

        return host;
    }

    private static void resolveAndBlockPrivate(String host, String url) {
        try {
            InetAddress address = InetAddress.getByName(host);

            // Standard IPv4/IPv6 private range checks via JDK
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Private IP addresses are not allowed: " + url);
            }

            // Additional IPv6 private range checks not covered by isSiteLocalAddress()
            if (address instanceof Inet6Address inet6) {
                blockPrivateIpv6(inet6, url);
            }

        } catch (UnknownHostException e) {
            // If we cannot resolve, let it through (may be a valid public DNS name),
            // but block obvious IP-like strings to prevent bypass via unresolvable IPs.
            if (looksLikeIpAddress(host)) {
                throw new IllegalArgumentException("Unresolvable IP-like address not allowed: " + url);
            }
        }
    }

    /**
     * Blocks IPv6 private ranges not fully caught by {@link InetAddress#isSiteLocalAddress()}:
     * <ul>
     *   <li>fc00::/7  — Unique Local Addresses (ULA), covers fd00::/8 incl. AWS fd00:ec2::254</li>
     *   <li>fe80::/10 — Link-local (covered by isLinkLocalAddress but explicit for clarity)</li>
     *   <li>::1/128   — loopback (covered by isLoopbackAddress)</li>
     * </ul>
     */
    private static void blockPrivateIpv6(@NonNull Inet6Address address, @NonNull String url) {
        byte[] addr = address.getAddress(); // 16 bytes

        // fc00::/7 — first octet high bit pattern 1111110x
        // Catches fc00::/8 and fd00::/8 (both ULA ranges)
        if ((addr[0] & 0xFE) == 0xFC) {
            throw new IllegalArgumentException("Private IPv6 ULA address (fc00::/7) not allowed: " + url);
        }

        // fe80::/10 — link-local (first 10 bits = 1111111010)
        if ((addr[0] & 0xFF) == 0xFE && (addr[1] & 0xC0) == 0x80) {
            throw new IllegalArgumentException("Link-local IPv6 address (fe80::/10) not allowed: " + url);
        }
    }

    private static boolean looksLikeIpAddress(@NonNull String host) {
        boolean allDigitsAndDots = true;
        int dotCount = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') {
                dotCount++;
            } else if (c < '0' || c > '9') {
                allDigitsAndDots = false;
                break;
            }
        }
        return allDigitsAndDots && dotCount >= 1;
    }
}
