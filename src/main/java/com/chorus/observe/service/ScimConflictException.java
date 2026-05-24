package com.chorus.observe.service;

public class ScimConflictException extends RuntimeException {
    public ScimConflictException(String message) {
        super(message);
    }
}
