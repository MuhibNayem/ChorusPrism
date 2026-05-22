package com.chorus.observe.config;

/**
 * Thrown when the requested API version is not supported.
 */
public class UnsupportedApiVersionException extends IllegalArgumentException {
    public UnsupportedApiVersionException(String message) {
        super(message);
    }
}
