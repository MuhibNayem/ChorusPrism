package com.chorus.observe.service;

import com.chorus.observe.eval.HallucinationScorer;
import com.chorus.observe.model.Evaluator;
import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.RunEvaluation;
import com.chorus.observe.persistence.EvaluatorRepository;
import com.chorus.observe.persistence.LlmCallRepository;
import com.chorus.observe.persistence.RunEvaluationRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
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
    private final LlmCallRepository llmCallRepository;
    private final HallucinationScorer ngramScorer;
    private final HallucinationScorer llmJudgeScorer;

    public EvaluatorService(
            @NonNull EvaluatorRepository evaluatorRepository,
            @NonNull RunEvaluationRepository runEvaluationRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull HallucinationScorer ngramScorer,
            @Nullable HallucinationScorer llmJudgeScorer) {
        this.evaluatorRepository = Objects.requireNonNull(evaluatorRepository);
        this.runEvaluationRepository = Objects.requireNonNull(runEvaluationRepository);
        this.llmCallRepository = Objects.requireNonNull(llmCallRepository);
        this.ngramScorer = Objects.requireNonNull(ngramScorer);
        this.llmJudgeScorer = llmJudgeScorer;
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
        Evaluator evaluator = evaluatorRepository.findById(evaluatorId)
            .orElseThrow(() -> new IllegalArgumentException("Evaluator not found: " + evaluatorId));

        String evaluationId = "re-" + UUID.randomUUID().toString().substring(0, 8);
        List<LlmCall> calls = llmCallRepository.findByRunId(runId);

        double score;
        boolean passed;
        Map<String, Object> details;

        if ("hallucination".equals(evaluator.kind())) {
            double threshold = evaluator.config().get("threshold") instanceof Number n
                ? n.doubleValue() : 0.7;
            int ngramSize = evaluator.config().get("ngramSize") instanceof Number n
                ? n.intValue() : 2;
            Map<String, Object> scorerConfig = new HashMap<>(evaluator.config());
            scorerConfig.put("ngramSize", ngramSize);

            score = ngramScorer.score(calls, scorerConfig);

            if (llmJudgeScorer != null && evaluator.config().containsKey("llmJudgeUrl")) {
                double judgeScore = llmJudgeScorer.score(calls, evaluator.config());
                score = (score + judgeScore) / 2.0;
            }

            passed = score >= threshold;
            details = Map.of(
                "scorer", llmJudgeScorer != null && evaluator.config().containsKey("llmJudgeUrl") ? "hybrid" : "ngram",
                "threshold", threshold,
                "ngramSize", ngramSize,
                "callCount", calls.size()
            );
        } else {
            score = 0.0;
            passed = false;
            details = Map.of("status", "pending", "reason", "kind not implemented: " + evaluator.kind());
        }

        RunEvaluation evaluation = new RunEvaluation(
            evaluationId, runId, evaluatorId, score, passed, details
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
