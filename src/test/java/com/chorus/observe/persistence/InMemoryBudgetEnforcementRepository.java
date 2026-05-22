package com.chorus.observe.persistence;

import com.chorus.observe.model.BudgetEnforcement;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryBudgetEnforcementRepository extends BudgetEnforcementRepository {
    private final Map<String, BudgetEnforcement> store = new HashMap<>();

    public InMemoryBudgetEnforcementRepository() {
        super(null);
    }

    @Override
    public void save(BudgetEnforcement enforcement) {
        store.put(enforcement.enforcementId(), enforcement);
    }

    @Override
    public Optional<BudgetEnforcement> findById(String enforcementId) {
        return Optional.ofNullable(store.get(enforcementId));
    }

    @Override
    public List<BudgetEnforcement> findByAgentId(String agentId) {
        return store.values().stream().filter(b -> b.agentId().equals(agentId)).sorted(Comparator.comparing(BudgetEnforcement::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<BudgetEnforcement> findByAgentId(String agentId, int limit, int offset) {
        return store.values().stream().filter(b -> b.agentId().equals(agentId)).sorted(Comparator.comparing(BudgetEnforcement::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByAgentId(String agentId) {
        return store.values().stream().filter(b -> b.agentId().equals(agentId)).count();
    }

    @Override
    public List<BudgetEnforcement> findAll() {
        return store.values().stream().sorted(Comparator.comparing(BudgetEnforcement::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<BudgetEnforcement> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(BudgetEnforcement::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public List<BudgetEnforcement> findActive() {
        return store.values().stream().filter(b -> b.status() == BudgetEnforcement.Status.ACTIVE).sorted(Comparator.comparing(BudgetEnforcement::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<BudgetEnforcement> findActive(int limit, int offset) {
        return store.values().stream().filter(b -> b.status() == BudgetEnforcement.Status.ACTIVE).sorted(Comparator.comparing(BudgetEnforcement::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countActive() {
        return store.values().stream().filter(b -> b.status() == BudgetEnforcement.Status.ACTIVE).count();
    }
}
