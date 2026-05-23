package com.chorus.observe.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Logs every request with correlation ID, latency, and response status.
 * Pushes trace context into SLF4J MDC for structured log correlation.
 */
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String START_ATTR = "requestStart";
    private static final String CORR_ID_ATTR = "correlationId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String correlationId = UUID.randomUUID().toString().substring(0, 16);
        request.setAttribute(START_ATTR, Instant.now());
        request.setAttribute(CORR_ID_ATTR, correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        MDC.put("traceId", correlationId);
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null) {
            MDC.put("tenantId", tenantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        Instant start = (Instant) request.getAttribute(START_ATTR);
        String correlationId = (String) request.getAttribute(CORR_ID_ATTR);
        long latencyMs = start != null ? Duration.between(start, Instant.now()).toMillis() : -1;
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();

        if (ex != null || status >= 500) {
            LOG.error("[{}] {} {} -> {} in {}ms | error={}", correlationId, method, path, status, latencyMs, ex != null ? ex.getMessage() : "none");
        } else if (status >= 400) {
            LOG.warn("[{}] {} {} -> {} in {}ms", correlationId, method, path, status, latencyMs);
        } else {
            LOG.info("[{}] {} {} -> {} in {}ms", correlationId, method, path, status, latencyMs);
        }

        MDC.clear();
    }
}
