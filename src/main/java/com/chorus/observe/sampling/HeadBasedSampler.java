package com.chorus.observe.sampling;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Head-based deterministic sampler.
 * <p>
 * The sampling decision is made at the start of a trace and cached so that
 * all spans within the same trace receive the same decision. Uses a hash of
 * the trace ID to achieve deterministic sampling without external state.
 */
public final class HeadBasedSampler implements Sampler {

    private final double rate;
    private final ConcurrentHashMap<String, Boolean> decisions = new ConcurrentHashMap<>();

    public HeadBasedSampler(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Sampling rate must be in [0.0, 1.0]");
        }
        this.rate = rate;
    }

    @Override
    public boolean shouldSample(@NonNull String traceId) {
        if (rate >= 1.0) return true;
        if (rate <= 0.0) return false;
        return decisions.computeIfAbsent(traceId, id -> {
            int hash = id.hashCode();
            // Use the upper 32 bits of a long to get a value in [0, 2^31)
            double normalized = (hash & 0x7fffffff) / (double) 0x7fffffff;
            return normalized < rate;
        });
    }
}
