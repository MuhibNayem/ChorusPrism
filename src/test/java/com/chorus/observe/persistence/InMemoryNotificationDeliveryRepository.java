package com.chorus.observe.persistence;

import com.chorus.observe.model.NotificationDelivery;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryNotificationDeliveryRepository extends NotificationDeliveryRepository {
    private final Map<String, NotificationDelivery> store = new HashMap<>();

    public InMemoryNotificationDeliveryRepository() {
        super(org.mockito.Mockito.mock(javax.sql.DataSource.class));
    }

    @Override
    public void save(@NonNull NotificationDelivery delivery) {
        store.put(delivery.deliveryId(), delivery);
    }

    @Override
    public @NonNull List<NotificationDelivery> findByEventId(@NonNull String eventId) {
        return store.values().stream()
            .filter(d -> d.eventId().equals(eventId))
            .sorted(Comparator.comparing(NotificationDelivery::createdAt).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public @NonNull List<NotificationDelivery> findByChannelId(@NonNull String channelId, int limit) {
        return store.values().stream()
            .filter(d -> d.channelId().equals(channelId))
            .sorted(Comparator.comparing(NotificationDelivery::createdAt).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public @NonNull List<NotificationDelivery> findDlq(int limit) {
        return store.values().stream()
            .filter(d -> d.status() == NotificationDelivery.Status.DLQ)
            .sorted(Comparator.comparing(NotificationDelivery::createdAt).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public long countByStatus(@NonNull String status) {
        try {
            NotificationDelivery.Status targetStatus = NotificationDelivery.Status.valueOf(status.toUpperCase());
            return store.values().stream()
                .filter(d -> d.status() == targetStatus)
                .count();
        } catch (IllegalArgumentException e) {
            return 0L;
        }
    }
}
