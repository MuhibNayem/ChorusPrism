package com.chorus.observe.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Interceptor that validates the {@code X-API-Version} header.
 * Defaults to {@code v1}. Returns 400 Bad Request for unsupported versions.
 */
public class ApiVersionInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-API-Version";
    private static final String DEFAULT_VERSION = "v1";
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("v1");

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String version = request.getHeader(HEADER_NAME);
        if (version == null || version.isBlank()) {
            version = DEFAULT_VERSION;
        }
        if (!SUPPORTED_VERSIONS.contains(version)) {
            throw new UnsupportedApiVersionException("Unsupported API version: " + version);
        }
        return true;
    }
}
