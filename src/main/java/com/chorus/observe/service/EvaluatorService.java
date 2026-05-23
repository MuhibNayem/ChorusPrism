package com.chorus.observe.service;

import com.chorus.observe.model.Evaluator;
import com.chorus.observe.model.RunEvaluation;
import com.chorus.observe.persistence.EvaluatorRepository;
import com.chorus.observe.persistence.RunEvaluationRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for evaluator management and run evaluation.
 */
public class EvaluatorService {

    private final EvaluatorRepository evaluatorRepository;
    private final RunEvaluationRepository runEvaluationRepository;

    public EvaluatorService(
            @NonNull EvaluatorRepository evaluatorRepository,
            @NonNull RunEvaluationRepository runEvaluationRepository) {
        this.evaluatorRepository = Objects.requireNonNull(evaluatorRepository);
        this.runEvaluationRepository = Objects.requireNonNull(runEvaluationRepository);
    }

    public @NonNull List<EvaluatorWithScore> listEvaluators() {
        List<Evaluator> evaluators = evaluatorRepository.findAll();
        return evaluators.stream()
            .map(e -> {
                double score24h = runEvaluationRepository.avgScoreByEvaluatorIdLast24h(e.evaluatorId());
                return new EvaluatorWithScore(e, score24h);
            })
            .toList();
    }

    public @NonNull Optional<Evaluator> getEvaluator(@NonNull String id) {
        return evaluatorRepository.findById(id);
    }

    public @NonNull Evaluator createEvaluator(
            @NonNull String name,
            @NonNull String kind,
            @Nullable String description,
            @NonNull Map<String, Object> config) {
        String evaluatorId = "ev-" + UUID.randomUUID().toString().substring(0, 8);
        Evaluator evaluator = new Evaluator(evaluatorId, name, kind, description, config);
        evaluatorRepository.save(evaluator);
        return evaluator;
    }

    public @NonNull RunEvaluation evaluateRun(@NonNull String runId, @NonNull String evaluatorId) {
        String evaluationId = "re-" + UUID.randomUUID().toString().substring(0, 8);
        RunEvaluation evaluation = new RunEvaluation(
            evaluationId, runId, evaluatorId, 0.0, false, Map.of("status", "pending")
        );
        runEvaluationRepository.save(evaluation);
        return evaluation;
    }

    public @NonNull List<RunEvaluation> getRunEvaluations(@NonNull String runId) {
        return runEvaluationRepository.findByRunId(runId);
    }

    public record EvaluatorWithScore(
        @NonNull Evaluator evaluator,
        double score24h
    ) {}
}
