package com.chorus.observe.service;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Decorates an {@link AgentInvoker} with circuit breaker logic.
 * <p>
 * If the underlying invoker throws {@link AgentInvocationException}, it is treated as a failure.
 * When the breaker is OPEN, a {@link CircuitBreakerOpenException} is thrown.
 */
public class CircuitBreakerAgentInvoker implements AgentInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(CircuitBreakerAgentInvoker.class);

    private final AgentInvoker delegate;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerAgentInvoker(@NonNull AgentInvoker delegate, @NonNull CircuitBreaker circuitBreaker) {
        this.delegate = Objects.requireNonNull(delegate);
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker);
    }

    @Override
    public @NonNull String invoke(@NonNull String agentConfig, @NonNull String input) {
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            throw new CircuitBreakerOpenException("Circuit breaker is OPEN for agent invoker");
        }

        try {
            String result = delegate.invoke(agentConfig, input);
            circuitBreaker.recordSuccess();
            return result;
        } catch (AgentInvocationException e) {
            circuitBreaker.recordFailure();
            throw e;
        }
    }
}
