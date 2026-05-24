package com.chorus.observe.persistence;

import com.chorus.observe.model.AlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryAlertEventRepository extends AlertEventRepository {
    private final Map<String, AlertEvent> store = new HashMap<>();

    public InMemoryAlertEventRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(AlertEvent event) {
        store.put(event.eventId(), event);
    }

    @Override
    public Optional<AlertEvent> findById(String eventId) {
        return Optional.ofNullable(store.get(eventId));
    }

    @Override
    public List<AlertEvent> findByRuleId(String ruleId) {
        return store.values().stream().filter(e -> e.ruleId().equals(ruleId)).sorted(Comparator.comparing(AlertEvent::triggeredAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<AlertEvent> findByRuleId(String ruleId, int limit, int offset) {
        return store.values().stream().filter(e -> e.ruleId().equals(ruleId)).sorted(Comparator.comparing(AlertEvent::triggeredAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByRuleId(String ruleId) {
        return store.values().stream().filter(e -> e.ruleId().equals(ruleId)).count();
    }

    @Override
    public List<AlertEvent> findUnresolved() {
        return store.values().stream().filter(e -> e.resolvedAt() == null).sorted(Comparator.comparing(AlertEvent::triggeredAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<AlertEvent> findUnresolved(int limit, int offset) {
        return store.values().stream().filter(e -> e.resolvedAt() == null).sorted(Comparator.comparing(AlertEvent::triggeredAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countUnresolved() {
        return store.values().stream().filter(e -> e.resolvedAt() == null).count();
    }

    @Override
    public List<AlertEvent> findRecent(int limit) {
        return store.values().stream().sorted(Comparator.comparing(AlertEvent::triggeredAt).reversed()).limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<AlertEvent> findRecent(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(AlertEvent::triggeredAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public Optional<AlertEvent> findMostRecentByRuleId(String ruleId) {
        return store.values().stream()
            .filter(e -> e.ruleId().equals(ruleId))
            .max(Comparator.comparing(AlertEvent::triggeredAt));
    }

    @Override
    public List<AlertEvent> findRetryable() {
        return store.values().stream()
            .filter(e -> e.nextRetryAt() != null && !e.nextRetryAt().isAfter(java.time.Instant.now()) && e.retryCount() < 3)
            .sorted(Comparator.comparing(AlertEvent::nextRetryAt))
            .collect(Collectors.toList());
    }
}
