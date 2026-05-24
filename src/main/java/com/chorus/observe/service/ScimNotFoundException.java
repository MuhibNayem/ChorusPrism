package com.chorus.observe.service;

public class ScimNotFoundException extends RuntimeException {
    public ScimNotFoundException(String message) {
        super(message);
    }
}
