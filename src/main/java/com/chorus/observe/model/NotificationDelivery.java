package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Records every attempt to deliver an alert notification to a channel.
 * Provides per-channel delivery tracking independent of the alert event retry counter.
 */
public record NotificationDelivery(
    @NonNull String deliveryId,
    @NonNull String eventId,
    @NonNull String channelId,
    @NonNull Status status,
    int attemptCount,
    @Nullable String lastError,
    @Nullable Instant sentAt,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public NotificationDelivery {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public enum Status {
        PENDING, SENT, FAILED, DLQ;

        /** Returns the lowercase string used in the database TEXT column. */
        public String toDbValue() {
            return name().toLowerCase();
        }

        public static Status fromDbValue(@NonNull String value) {
            return Status.valueOf(value.toUpperCase());
        }
    }
}
