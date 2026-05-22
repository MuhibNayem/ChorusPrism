package com.chorus.observe.service;

/**
 * Thrown when a {@link CircuitBreaker} is in OPEN state and rejects execution.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
