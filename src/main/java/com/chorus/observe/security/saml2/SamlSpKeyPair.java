package com.chorus.observe.security.saml2;

import org.jspecify.annotations.NonNull;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Generates and caches an ephemeral RSA key pair for SAML SP signing/decryption.
 * In production, this should be replaced with a persistent key loaded from configuration.
 */
public final class SamlSpKeyPair {

    private static final Object LOCK = new Object();
    private static KeyPair cached;

    private SamlSpKeyPair() {}

    public static @NonNull KeyPair get() {
        if (cached != null) {
            return cached;
        }
        synchronized (LOCK) {
            if (cached != null) {
                return cached;
            }
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                cached = generator.generateKeyPair();
                return cached;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("RSA algorithm not available", e);
            }
        }
    }
}
