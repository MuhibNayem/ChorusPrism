package com.chorus.observe.notification;

import org.jspecify.annotations.NonNull;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * SSRF-preventing URL validator for outbound HTTP requests.
 * <p>
 * Blocks private IP ranges, localhost, link-local addresses, and metadata endpoints.
 * Used by {@link WebhookDispatcher}, {@link S3ExportClient}, and any other
 * component that makes user-configurable outbound HTTP calls.
 */
public final class UrlValidator {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("file", "ftp", "gopher", "jar");

    private UrlValidator() {}

    /**
     * Validate that a URL is safe for outbound HTTP/HTTPS requests.
     *
     * @param url the URL string to validate
     * @throws IllegalArgumentException if the URL is unsafe
     */
    public static void validate(@NonNull String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL must have a scheme: " + url);
        }
        if (BLOCKED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException("URL scheme not allowed: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must have a host: " + url);
        }

        String lowerHost = host.toLowerCase();
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

        // Resolve and block private IP ranges
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Private IP addresses are not allowed: " + url);
            }
        } catch (UnknownHostException e) {
            // If we can't resolve, let it through (may be a public DNS name)
            // but block obvious IP-like strings
            if (looksLikeIpAddress(host)) {
                throw new IllegalArgumentException("Unresolvable IP-like address not allowed: " + url);
            }
        }
    }

    private static boolean looksLikeIpAddress(@NonNull String host) {
        // Simple heuristic: if it contains only digits and dots, it's likely an IP
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
