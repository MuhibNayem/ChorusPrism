package com.chorus.observe.security.saml2;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class AssertionIdCache {

    private static final Duration TTL = Duration.ofMinutes(2);
    private final ConcurrentHashMap<String, Instant> cache = new ConcurrentHashMap<>();

    public synchronized boolean isReplay(@NonNull String assertionId) {
        Instant now = Instant.now();
        cache.entrySet().removeIf(e -> e.getValue().isBefore(now));

        Instant seenAt = cache.get(assertionId);
        if (seenAt != null) {
            return true;
        }
        cache.put(assertionId, now.plus(TTL));
        return false;
    }
}
