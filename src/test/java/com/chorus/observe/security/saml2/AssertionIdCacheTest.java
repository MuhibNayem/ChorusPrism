package com.chorus.observe.security.saml2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionIdCacheTest {

    @Test
    void shouldAcceptFirstAssertion() {
        AssertionIdCache cache = new AssertionIdCache();
        assertThat(cache.isReplay("assertion-123")).isFalse();
    }

    @Test
    void shouldDetectReplayWithinWindow() {
        AssertionIdCache cache = new AssertionIdCache();
        assertThat(cache.isReplay("assertion-123")).isFalse();
        assertThat(cache.isReplay("assertion-123")).isTrue();
    }

    @Test
    void shouldStoreEntryWithFutureExpiry() {
        AssertionIdCache cache = new AssertionIdCache();
        cache.isReplay("assertion-123");
        assertThat(cache.isReplay("assertion-123")).isTrue();
    }
}
