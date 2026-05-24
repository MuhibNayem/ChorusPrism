package com.chorus.observe.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void shouldEncodePassword() {
        String encoded = encoder.encode("my-secret-password");
        assertThat(encoded).isNotNull().isNotBlank();
        assertThat(encoded).startsWith("$2a$");
    }

    @Test
    void shouldMatchCorrectPassword() {
        String encoded = encoder.encode("my-secret-password");
        assertThat(encoder.matches("my-secret-password", encoded)).isTrue();
    }

    @Test
    void shouldNotMatchIncorrectPassword() {
        String encoded = encoder.encode("my-secret-password");
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }
}
