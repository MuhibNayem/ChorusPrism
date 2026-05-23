package com.chorus.observe.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Servlet filter that creates a lightweight trace context for every request.
 * Populates MDC with traceId, spanId, tenantId, userId for structured logging.
 * Does NOT replace a full OpenTelemetry agent — this is a minimal self-tracing
 * layer so the observability server can observe itself.
 */
public class TracingFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(TracingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String traceId = extractOrGenerateTraceId(req);
        String spanId = UUID.randomUUID().toString().substring(0, 16);
        Instant start = Instant.now();

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);

        String tenantId = req.getHeader("X-Tenant-Id");
        String userId = req.getHeader("X-User-Id");
        if (tenantId != null) MDC.put("tenantId", tenantId);
        if (userId != null) MDC.put("userId", userId);

        res.setHeader("X-Trace-Id", traceId);
        res.setHeader("X-Span-Id", spanId);

        try {
            chain.doFilter(request, response);
        } finally {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            int status = res.getStatus();
            String method = req.getMethod();
            String path = req.getRequestURI();

            if (status >= 500) {
                LOG.error("trace={} span={} {} {} -> {} in {}ms", traceId, spanId, method, path, status, latencyMs);
            } else if (status >= 400) {
                LOG.warn("trace={} span={} {} {} -> {} in {}ms", traceId, spanId, method, path, status, latencyMs);
            } else {
                LOG.debug("trace={} span={} {} {} -> {} in {}ms", traceId, spanId, method, path, status, latencyMs);
            }

            MDC.clear();
        }
    }

    private @NonNull String extractOrGenerateTraceId(@NonNull HttpServletRequest req) {
        String header = req.getHeader("X-Trace-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
