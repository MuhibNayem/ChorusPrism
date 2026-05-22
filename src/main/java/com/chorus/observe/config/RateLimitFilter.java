package com.chorus.observe.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiting filter per client IP / API key.
 * <p>
 * Tokens are replenished continuously at {@code ratePerSecond = maxRequestsPerMinute / 60.0}.
 * Each request consumes one token. If the bucket is empty, the request is rejected with 429.
 * <p>
 * This avoids the thundering-herd problem of fixed-window counters at boundary transitions.
 * Production deployments should use a distributed token bucket (e.g., Redis + Lua).
 */
public class RateLimitFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    private final double maxRequestsPerMinute;
    private final boolean enabled;
    private final double ratePerSecond;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(double maxRequestsPerMinute, boolean enabled) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.enabled = enabled;
        this.ratePerSecond = maxRequestsPerMinute / 60.0;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!enabled || maxRequestsPerMinute <= 0) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String clientId = resolveClientId(req);

        TokenBucket bucket = buckets.computeIfAbsent(clientId, k -> new TokenBucket(maxRequestsPerMinute));
        if (!bucket.tryConsume(1.0, ratePerSecond)) {
            LOG.warn("Rate limit exceeded for client: {} (bucket: {})", clientId, bucket);
            res.setStatus(429);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setHeader("Retry-After", "60");
            res.setHeader("X-RateLimit-Limit", String.valueOf((int) maxRequestsPerMinute));
            res.getWriter().write(jsonError("Rate limit exceeded. Try again later."));
            return;
        }

        chain.doFilter(request, response);
    }

    private @NonNull String resolveClientId(@NonNull HttpServletRequest req) {
        String apiKey = req.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey;
        }
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return req.getRemoteAddr();
    }

    private @NonNull String jsonError(@NonNull String message) {
        return "{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"" + message + "\"}";
    }

    /**
     * Simple token bucket. Thread-safe via synchronized internal state.
     */
    private static final class TokenBucket {
        private final double capacity;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(double capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume(double amount, double ratePerSecond) {
            refill(ratePerSecond);
            if (tokens >= amount) {
                tokens -= amount;
                return true;
            }
            return false;
        }

        private void refill(double ratePerSecond) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            double newTokens = elapsedSeconds * ratePerSecond;
            if (newTokens > 0) {
                tokens = Math.min(capacity, tokens + newTokens);
                lastRefillNanos = now;
            }
        }

        @Override
        public synchronized String toString() {
            return String.format("TokenBucket{tokens=%.2f/%s}", tokens, capacity);
        }
    }
}
