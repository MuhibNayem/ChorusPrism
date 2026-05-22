package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;

public record AlertRuleChannel(
    @NonNull String ruleId,
    @NonNull String channelId,
    @NonNull Instant createdAt
) {
    public AlertRuleChannel {
        Objects.requireNonNull(ruleId);
        Objects.requireNonNull(channelId);
        Objects.requireNonNull(createdAt);
    }
}
