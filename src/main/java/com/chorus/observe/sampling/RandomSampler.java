package com.chorus.observe.sampling;

import org.jspecify.annotations.NonNull;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Probabilistic random sampler.
 * <p>
 * Traces are sampled independently with probability {@code rate}.
 * A rate of 1.0 samples everything; 0.0 samples nothing.
 */
public final class RandomSampler implements Sampler {

    private final double rate;
    private final Random random;

    public RandomSampler(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Sampling rate must be in [0.0, 1.0]");
        }
        this.rate = rate;
        this.random = new SecureRandom();
    }

    @Override
    public boolean shouldSample(@NonNull String traceId) {
        if (rate >= 1.0) return true;
        if (rate <= 0.0) return false;
        return random.nextDouble() < rate;
    }
}
