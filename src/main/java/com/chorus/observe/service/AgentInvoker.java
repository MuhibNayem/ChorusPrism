package com.chorus.observe.service;

import org.jspecify.annotations.NonNull;

/**
 * Invokes an agent with a given input during evaluation.
 * Implementations may call an HTTP endpoint or invoke an in-process agent.
 */
public interface AgentInvoker {

    /**
     * Invoke the agent with the given configuration and input.
     *
     * @param agentConfig JSON configuration for the agent (endpoint, model, etc.)
     * @param input       the input prompt or question
     * @return the agent's output text
     */
    @NonNull String invoke(@NonNull String agentConfig, @NonNull String input);
}
