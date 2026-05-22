package com.chorus.observe.persistence;

import com.chorus.observe.model.RedTeamScenario;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryRedTeamScenarioRepository extends RedTeamScenarioRepository {
    private final Map<String, RedTeamScenario> store = new HashMap<>();

    public InMemoryRedTeamScenarioRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(RedTeamScenario scenario) {
        store.put(scenario.scenarioId(), scenario);
    }

    @Override
    public Optional<RedTeamScenario> findById(String scenarioId) {
        return Optional.ofNullable(store.get(scenarioId));
    }

    @Override
    public List<RedTeamScenario> findAll() {
        return store.values().stream().sorted(Comparator.comparing(RedTeamScenario::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<RedTeamScenario> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(RedTeamScenario::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public List<RedTeamScenario> findByCategory(String category) {
        return store.values().stream().filter(s -> s.category().equals(category)).sorted(Comparator.comparing(RedTeamScenario::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<RedTeamScenario> findByCategory(String category, int limit, int offset) {
        return store.values().stream().filter(s -> s.category().equals(category)).sorted(Comparator.comparing(RedTeamScenario::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByCategory(String category) {
        return store.values().stream().filter(s -> s.category().equals(category)).count();
    }

    @Override
    public List<RedTeamScenario> findBySeverity(RedTeamScenario.Severity severity) {
        return store.values().stream().filter(s -> s.severity() == severity).sorted(Comparator.comparing(RedTeamScenario::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<RedTeamScenario> findBySeverity(RedTeamScenario.Severity severity, int limit, int offset) {
        return store.values().stream().filter(s -> s.severity() == severity).sorted(Comparator.comparing(RedTeamScenario::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countBySeverity(RedTeamScenario.Severity severity) {
        return store.values().stream().filter(s -> s.severity() == severity).count();
    }

    @Override
    public void deleteById(String scenarioId) {
        store.remove(scenarioId);
    }
}
