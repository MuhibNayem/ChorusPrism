package com.chorus.observe.persistence;

import com.chorus.observe.model.RunEvaluation;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryRunEvaluationRepository extends RunEvaluationRepository {
    private final List<RunEvaluation> store = new ArrayList<>();

    public InMemoryRunEvaluationRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(RunEvaluation evaluation) {
        store.removeIf(e -> e.evaluationId().equals(evaluation.evaluationId()));
        store.add(evaluation);
    }

    @Override
    public List<RunEvaluation> findByRunId(String runId) {
        return store.stream()
            .filter(e -> e.runId().equals(runId))
            .collect(Collectors.toList());
    }

    @Override
    public List<RunEvaluation> findByRunIds(List<String> runIds) {
        return store.stream()
            .filter(e -> runIds.contains(e.runId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<RunEvaluation> findByEvaluatorId(String evaluatorId) {
        return store.stream()
            .filter(e -> e.evaluatorId().equals(evaluatorId))
            .collect(Collectors.toList());
    }

    @Override
    public long countByEvaluatorId(String evaluatorId) {
        return store.stream().filter(e -> e.evaluatorId().equals(evaluatorId)).count();
    }

    @Override
    public double avgScoreByEvaluatorId(String evaluatorId) {
        return store.stream()
            .filter(e -> e.evaluatorId().equals(evaluatorId))
            .mapToDouble(RunEvaluation::score)
            .average()
            .orElse(0.0);
    }

    @Override
    public double avgScoreByEvaluatorIdLast24h(String evaluatorId) {
        return avgScoreByEvaluatorId(evaluatorId);
    }
}
