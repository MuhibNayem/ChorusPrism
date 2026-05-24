package com.chorus.observe.persistence;

import com.chorus.observe.model.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryNotificationChannelRepository extends NotificationChannelRepository {
    private final Map<String, NotificationChannel> store = new HashMap<>();

    public InMemoryNotificationChannelRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(NotificationChannel channel) {
        store.put(channel.channelId(), channel);
    }

    @Override
    public Optional<NotificationChannel> findById(String channelId) {
        return Optional.ofNullable(store.get(channelId));
    }

    @Override
    public List<NotificationChannel> findByTenant(String tenantId) {
        return store.values().stream()
            .filter(c -> c.tenantId().equals(tenantId))
            .sorted(Comparator.comparing(NotificationChannel::createdAt).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public List<NotificationChannel> findEnabledByTenant(String tenantId) {
        return store.values().stream()
            .filter(c -> c.tenantId().equals(tenantId) && c.enabled())
            .sorted(Comparator.comparing(NotificationChannel::createdAt).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String channelId) {
        store.remove(channelId);
    }
}
