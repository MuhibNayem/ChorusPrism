package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record NotificationChannel(
    @NonNull String channelId,
    @NonNull String tenantId,
    @NonNull String name,
    @NonNull ChannelType channelType,
    @NonNull Map<String, Object> config,
    boolean enabled,
    @NonNull Instant lastUsedAt,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public NotificationChannel {
        Objects.requireNonNull(channelId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(channelType);
        Objects.requireNonNull(config);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public enum ChannelType { SLACK, PAGERDUTY, EMAIL, WEBHOOK }
}
