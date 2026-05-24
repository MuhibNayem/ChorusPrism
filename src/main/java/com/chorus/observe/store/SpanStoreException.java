package com.chorus.observe.store;

/**
 * Runtime exception thrown when a {@link SpanStore} operation fails.
 * Signals to callers that span data was NOT persisted so they can
 * retry, backpressure, or alert operators.
 */
public class SpanStoreException extends RuntimeException {

    public SpanStoreException(String message) {
        super(message);
    }

    public SpanStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
