package com.chorus.observe.sampling;

import org.jspecify.annotations.NonNull;

/**
 * Tail-based sampler that keeps traces based on their outcome.
 * <p>
 * Always samples traces with errors or high latency, regardless of the base rate.
 * For normal traces, falls back to the base sampling rate.
 */
public final class TailBasedSampler implements Sampler {

    private final double baseRate;
    private final long latencyThresholdMs;

    public TailBasedSampler(double baseRate, long latencyThresholdMs) {
        if (baseRate < 0.0 || baseRate > 1.0) {
            throw new IllegalArgumentException("Base rate must be in [0.0, 1.0]");
        }
        this.baseRate = baseRate;
        this.latencyThresholdMs = latencyThresholdMs;
    }

    @Override
    public boolean shouldSample(@NonNull String traceId) {
        return new java.security.SecureRandom().nextDouble() < baseRate;
    }

    @Override
    public boolean shouldSample(@NonNull String traceId, boolean hasError, long latencyMs) {
        if (hasError) return true;
        if (latencyMs >= latencyThresholdMs) return true;
        return shouldSample(traceId);
    }
}
