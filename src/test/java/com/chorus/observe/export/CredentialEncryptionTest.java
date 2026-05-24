package com.chorus.observe.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialEncryptionTest {

    @Test
    void roundTripEncryption() {
        CredentialEncryptionService service = new CredentialEncryptionService("my-super-secret-master-key-32chars!");
        String plaintext = "AKIAIOSFODNN7EXAMPLE";

        String encrypted = service.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encrypted).isBase64();

        String decrypted = service.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void disabledWhenNoMasterKey() {
        CredentialEncryptionService service = new CredentialEncryptionService(null);
        assertThat(service.isEnabled()).isFalse();
        assertThat(service.encrypt("plain")).isEqualTo("plain");
        assertThat(service.decrypt("plain")).isEqualTo("plain");
    }
}
