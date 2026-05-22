package com.chorus.observe.persistence;

import com.chorus.observe.model.EvalRun;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryEvalRunRepository extends EvalRunRepository {
    private final Map<String, EvalRun> store = new HashMap<>();

    public InMemoryEvalRunRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(EvalRun evalRun) {
        store.put(evalRun.evalRunId(), evalRun);
    }

    @Override
    public Optional<EvalRun> findById(String evalRunId) {
        return Optional.ofNullable(store.get(evalRunId));
    }

    @Override
    public List<EvalRun> findByDatasetId(String datasetId) {
        return store.values().stream().filter(e -> e.datasetId().equals(datasetId)).sorted(Comparator.comparing(EvalRun::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<EvalRun> findByDatasetId(String datasetId, int limit, int offset) {
        return store.values().stream().filter(e -> e.datasetId().equals(datasetId)).sorted(Comparator.comparing(EvalRun::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByDatasetId(String datasetId) {
        return store.values().stream().filter(e -> e.datasetId().equals(datasetId)).count();
    }

    @Override
    public List<EvalRun> findAll() {
        return store.values().stream().sorted(Comparator.comparing(EvalRun::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<EvalRun> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(EvalRun::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public List<EvalRun> findByStatus(EvalRun.Status status) {
        return store.values().stream().filter(e -> e.status() == status).sorted(Comparator.comparing(EvalRun::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<EvalRun> findByStatus(EvalRun.Status status, int limit, int offset) {
        return store.values().stream().filter(e -> e.status() == status).sorted(Comparator.comparing(EvalRun::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(EvalRun.Status status) {
        return store.values().stream().filter(e -> e.status() == status).count();
    }

    @Override
    public void deleteById(String evalRunId) {
        store.remove(evalRunId);
    }
}
