package com.chorus.observe.persistence;

import com.chorus.observe.model.AlertRule;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryAlertRuleRepository extends AlertRuleRepository {
    private final Map<String, AlertRule> store = new HashMap<>();

    public InMemoryAlertRuleRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(AlertRule rule) {
        store.put(rule.ruleId(), rule);
    }

    @Override
    public Optional<AlertRule> findById(String ruleId) {
        return Optional.ofNullable(store.get(ruleId));
    }

    @Override
    public List<AlertRule> findAll() {
        return store.values().stream().sorted(Comparator.comparing(AlertRule::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<AlertRule> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(AlertRule::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public List<AlertRule> findEnabled() {
        return store.values().stream().filter(AlertRule::enabled).sorted(Comparator.comparing(AlertRule::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<AlertRule> findEnabled(int limit, int offset) {
        return store.values().stream().filter(AlertRule::enabled).sorted(Comparator.comparing(AlertRule::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countEnabled() {
        return store.values().stream().filter(AlertRule::enabled).count();
    }

    @Override
    public void deleteById(String ruleId) {
        store.remove(ruleId);
    }
}
