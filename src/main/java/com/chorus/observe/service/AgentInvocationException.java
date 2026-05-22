package com.chorus.observe.service;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when an agent invocation fails (HTTP error, timeout, network failure).
 * Carries the HTTP status code and response body for diagnostics.
 */
public final class AgentInvocationException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public AgentInvocationException(@NonNull String message, int statusCode, @Nullable String responseBody) {
        super(message + " (HTTP " + statusCode + ")");
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public AgentInvocationException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public int statusCode() { return statusCode; }
    public @Nullable String responseBody() { return responseBody; }

    public boolean isRetryable() {
        return statusCode == -1 || statusCode == 429 || statusCode >= 500;
    }
}
