package com.chorus.observe.persistence;

import com.chorus.observe.model.Evaluator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryEvaluatorRepository extends EvaluatorRepository {
    private final Map<String, Evaluator> store = new HashMap<>();

    public InMemoryEvaluatorRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(Evaluator evaluator) {
        store.put(evaluator.evaluatorId(), evaluator);
    }

    @Override
    public Optional<Evaluator> findById(String evaluatorId) {
        return Optional.ofNullable(store.get(evaluatorId));
    }

    @Override
    public List<Evaluator> findAll() {
        return store.values().stream()
            .sorted(Comparator.comparing(Evaluator::evaluatorId))
            .collect(Collectors.toList());
    }

    @Override
    public List<Evaluator> findByKind(String kind) {
        return store.values().stream()
            .filter(e -> e.kind().equals(kind))
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String evaluatorId) {
        store.remove(evaluatorId);
    }
}
