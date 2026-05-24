package com.chorus.observe.export;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * AES-256-GCM encryption for S3 credentials at rest.
 * <p>
 * <b>Version 2 (current):</b> Master key derived via PBKDF2-HMAC-SHA256
 * (100,000 iterations) with a random 16-byte salt. The salt is embedded in
 * the ciphertext payload, so each encryption uses a unique salt.
 * <p>
 * <b>Version 1 (legacy):</b> Master key derived via plain SHA-256(passphrase)
 * with no salt. Still supported for decryption of existing data.
 * <p>
 * Encrypted payload format (Version 2):
 * <pre>
 *   [1 byte version=0x02] [16 bytes salt] [12 bytes IV] [N bytes ciphertext+tag]
 * </pre>
 * Encrypted payload format (Version 1, legacy):
 * <pre>
 *   [12 bytes IV] [N bytes ciphertext+tag]
 * </pre>
 */
public class CredentialEncryptionService {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialEncryptionService.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // PBKDF2 parameters
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int PBKDF2_SALT_LENGTH = 16;
    private static final int PBKDF2_KEY_LENGTH = 256;

    // Version marker for new format
    private static final byte VERSION_2 = 0x02;

    private final String masterPassphrase;
    private final boolean enabled;

    public CredentialEncryptionService(@Nullable String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            LOG.warn("No export encryption master key configured. Credentials will be stored in plaintext.");
            this.masterPassphrase = null;
            this.enabled = false;
        } else {
            this.masterPassphrase = masterKey;
            this.enabled = true;
        }
    }

    public @NonNull String encrypt(@NonNull String plaintext) {
        Objects.requireNonNull(plaintext);
        if (!enabled) return plaintext;

        try {
            byte[] salt = new byte[PBKDF2_SALT_LENGTH];
            new SecureRandom().nextBytes(salt);
            SecretKey secretKey = deriveKeyPbKdf2(masterPassphrase, salt);

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(1 + salt.length + iv.length + ciphertext.length);
            buffer.put(VERSION_2);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public @NonNull String decrypt(@NonNull String ciphertext) {
        Objects.requireNonNull(ciphertext);
        if (!enabled) return ciphertext;

        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            if (decoded.length == 0) return ciphertext;

            // Detect version
            boolean isV2 = decoded[0] == VERSION_2;

            if (isV2) {
                return decryptV2(decoded);
            } else {
                return decryptV1(decoded);
            }
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private @NonNull String decryptV2(byte[] decoded) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(decoded);
        buffer.get(); // skip version byte

        byte[] salt = new byte[PBKDF2_SALT_LENGTH];
        buffer.get(salt);

        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);

        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        SecretKey secretKey = deriveKeyPbKdf2(masterPassphrase, salt);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] plaintext = cipher.doFinal(encrypted);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private @NonNull String decryptV1(byte[] decoded) throws Exception {
        // Legacy format: no version byte, no salt — just IV + ciphertext
        ByteBuffer buffer = ByteBuffer.wrap(decoded);

        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        SecretKey secretKey = deriveKeySha256(masterPassphrase);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] plaintext = cipher.doFinal(encrypted);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static SecretKey deriveKeyPbKdf2(@NonNull String passphrase, @NonNull byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    private static SecretKey deriveKeySha256(@NonNull String passphrase) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive key", e);
        }
    }
}
