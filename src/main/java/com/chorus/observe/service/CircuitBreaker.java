package com.chorus.observe.service;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Simple circuit breaker with CLOSED, OPEN, and HALF_OPEN states.
 * <p>
 * After {@code failureThreshold} consecutive failures the breaker transitions to OPEN.
 * After the configured timeout it transitions to HALF_OPEN, allowing a single trial
 * request. On success it closes again; on failure it reopens.
 */
public class CircuitBreaker {

    private static final Logger LOG = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openTimeout;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile Instant lastFailureTime;

    public CircuitBreaker(int failureThreshold, @NonNull Duration openTimeout) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openTimeout = openTimeout;
    }

    /**
     * Returns the current state, automatically transitioning from OPEN to HALF_OPEN
     * when the timeout has elapsed.
     */
    public State getState() {
        State current = state.get();
        if (current == State.OPEN
                && lastFailureTime != null
                && Duration.between(lastFailureTime, Instant.now()).compareTo(openTimeout) >= 0) {
            if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                LOG.info("Circuit breaker transitioned from OPEN to HALF_OPEN");
                failureCount.set(0);
            }
            return state.get();
        }
        return current;
    }

    /**
     * Executes the supplier if the breaker permits it.
     *
     * @throws CircuitBreakerOpenException if the breaker is OPEN
     */
    public <T> T execute(@NonNull Supplier<T> supplier) {
        if (getState() == State.OPEN) {
            throw new CircuitBreakerOpenException("Circuit breaker is OPEN");
        }

        try {
            T result = supplier.get();
            recordSuccess();
            return result;
        } catch (CircuitBreakerOpenException e) {
            throw e;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }

    /**
     * Records a successful call. In HALF_OPEN state this closes the breaker.
     */
    public void recordSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                LOG.info("Circuit breaker transitioned from HALF_OPEN to CLOSED");
            }
        }
        failureCount.set(0);
    }

    /**
     * Records a failed call. In HALF_OPEN state this reopens the breaker;
     * in CLOSED state it may open the breaker once the threshold is reached.
     */
    public void recordFailure() {
        lastFailureTime = Instant.now();
        int failures = failureCount.incrementAndGet();

        State current = state.get();
        if (current == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                LOG.warn("Circuit breaker transitioned from HALF_OPEN to OPEN");
            }
        } else if (current == State.CLOSED && failures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                LOG.warn("Circuit breaker transitioned from CLOSED to OPEN after {} failures", failures);
            }
        }
    }
}
