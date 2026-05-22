package com.chorus.observe.persistence;

import com.chorus.observe.model.EvalResultRecord;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryEvalResultRepository extends EvalResultRepository {
    private final Map<String, EvalResultRecord> store = new HashMap<>();

    public InMemoryEvalResultRepository() {
        super(null);
    }

    @Override
    public void save(EvalResultRecord result) {
        store.put(result.resultId(), result);
    }

    @Override
    public Optional<EvalResultRecord> findById(String resultId) {
        return Optional.ofNullable(store.get(resultId));
    }

    @Override
    public List<EvalResultRecord> findByEvalRunId(String evalRunId) {
        return store.values().stream().filter(r -> r.evalRunId().equals(evalRunId)).sorted(Comparator.comparing(EvalResultRecord::createdAt)).collect(Collectors.toList());
    }

    @Override
    public List<EvalResultRecord> findByEvalRunId(String evalRunId, int limit, int offset) {
        return store.values().stream().filter(r -> r.evalRunId().equals(evalRunId)).sorted(Comparator.comparing(EvalResultRecord::createdAt)).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByEvalRunId(String evalRunId) {
        return store.values().stream().filter(r -> r.evalRunId().equals(evalRunId)).count();
    }

    @Override
    public List<EvalResultRecord> findByRunId(String runId) {
        return store.values().stream().filter(r -> runId.equals(r.runId())).sorted(Comparator.comparing(EvalResultRecord::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<EvalResultRecord> findByRunId(String runId, int limit, int offset) {
        return store.values().stream().filter(r -> runId.equals(r.runId())).sorted(Comparator.comparing(EvalResultRecord::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByRunId(String runId) {
        return store.values().stream().filter(r -> runId.equals(r.runId())).count();
    }

    @Override
    public List<EvalResultRecord> findByRunIds(List<String> runIds) {
        Set<String> runIdSet = new HashSet<>(runIds);
        return store.values().stream()
            .filter(r -> r.runId() != null && runIdSet.contains(r.runId()))
            .sorted(Comparator.comparing(EvalResultRecord::createdAt).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public long countPassedByEvalRunId(String evalRunId) {
        return store.values().stream().filter(r -> r.evalRunId().equals(evalRunId) && r.passed()).count();
    }

    @Override
    public double avgScoreByEvalRunId(String evalRunId) {
        return store.values().stream().filter(r -> r.evalRunId().equals(evalRunId)).mapToDouble(EvalResultRecord::score).average().orElse(0.0);
    }

    @Override
    public void deleteByEvalRunId(String evalRunId) {
        store.values().removeIf(r -> r.evalRunId().equals(evalRunId));
    }
}
