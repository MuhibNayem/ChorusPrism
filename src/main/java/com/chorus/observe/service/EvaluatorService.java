package com.chorus.observe.service;

import com.chorus.observe.model.Run;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.eval.HallucinationScorer;
import com.chorus.observe.model.Evaluator;
import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.RunEvaluation;
import com.chorus.observe.persistence.EvaluatorRepository;
import com.chorus.observe.persistence.LlmCallRepository;
import com.chorus.observe.persistence.RunEvaluationRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final RunRepository runRepository;
    private final AgentInvoker agentInvoker;
    private final HallucinationScorer ngramScorer;
    private final HallucinationScorer llmJudgeScorer;

    public EvaluatorService(
            @NonNull EvaluatorRepository evaluatorRepository,
            @NonNull RunEvaluationRepository runEvaluationRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull RunRepository runRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull HallucinationScorer ngramScorer,
            @Nullable HallucinationScorer llmJudgeScorer) {
        this.evaluatorRepository = Objects.requireNonNull(evaluatorRepository);
        this.runEvaluationRepository = Objects.requireNonNull(runEvaluationRepository);
        this.llmCallRepository = Objects.requireNonNull(llmCallRepository);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.agentInvoker = Objects.requireNonNull(agentInvoker);
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
        } else if ("regex".equals(evaluator.kind())) {
            String patternStr = evaluator.config().getOrDefault("pattern", "").toString();
            String matchBehavior = evaluator.config().getOrDefault("matchBehavior", "must_match").toString();
            String target = evaluator.config().getOrDefault("target", "completion").toString();

            StringBuilder targetTextBuilder = new StringBuilder();
            for (LlmCall call : calls) {
                if ("prompt".equals(target) || "both".equals(target)) {
                    if (call.prompt() != null) targetTextBuilder.append(call.prompt()).append(" ");
                }
                if ("completion".equals(target) || "both".equals(target)) {
                    if (call.completion() != null) targetTextBuilder.append(call.completion()).append(" ");
                }
            }
            String targetText = targetTextBuilder.toString().trim();
            boolean matches = false;
            if (!patternStr.isEmpty() && !targetText.isEmpty()) {
                try {
                    matches = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE).matcher(targetText).find();
                } catch (Exception e) {
                    // Ignore regex syntax errors
                }
            }

            boolean checkPassed = "must_match".equals(matchBehavior) ? matches : !matches;
            score = checkPassed ? 1.0 : 0.0;
            passed = checkPassed;
            details = Map.of(
                "pattern", patternStr,
                "matchBehavior", matchBehavior,
                "matched", matches,
                "target", target
            );
        } else if ("rule".equals(evaluator.kind())) {
            String metric = evaluator.config().getOrDefault("metric", "latency_ms").toString();
            String operator = evaluator.config().getOrDefault("operator", "<=").toString();
            double threshold = evaluator.config().get("threshold") instanceof Number n ? n.doubleValue() : 3000.0;

            Optional<Run> runOpt = runRepository.findById(runId);
            double metricValue = 0.0;
            if (runOpt.isPresent()) {
                Run run = runOpt.get();
                if ("latency_ms".equals(metric)) {
                    metricValue = run.latencyMs();
                } else if ("total_tokens".equals(metric)) {
                    metricValue = run.totalTokens();
                } else if ("total_cost".equals(metric)) {
                    metricValue = run.totalCost().doubleValue();
                }
            }

            boolean checkPassed = false;
            switch (operator) {
                case "<" -> checkPassed = metricValue < threshold;
                case "<=" -> checkPassed = metricValue <= threshold;
                case ">" -> checkPassed = metricValue > threshold;
                case ">=" -> checkPassed = metricValue >= threshold;
                case "==" -> checkPassed = Math.abs(metricValue - threshold) < 0.0001;
            }

            score = checkPassed ? 1.0 : 0.0;
            passed = checkPassed;
            details = Map.of(
                "metric", metric,
                "operator", operator,
                "threshold", threshold,
                "actualValue", metricValue
            );
        } else if ("llm-judge".equals(evaluator.kind())) {
            double threshold = evaluator.config().get("threshold") instanceof Number n ? n.doubleValue() : 0.75;
            String promptTemplate = evaluator.config().getOrDefault("promptTemplate", "").toString();
            if (promptTemplate.isEmpty()) {
                promptTemplate = """
                    You are an expert evaluator. Rate how well the completion answers the prompt.

                    PROMPT: {prompt}

                    COMPLETION: {completion}

                    Provide a rating from 0 to 10, where 10 means the completion is perfect.
                    Start your response with the numeric rating, followed by an explanation.
                    """;
            }

            StringBuilder promptBuilder = new StringBuilder();
            StringBuilder completionBuilder = new StringBuilder();
            for (LlmCall call : calls) {
                if (call.prompt() != null) promptBuilder.append(call.prompt()).append("\n");
                if (call.completion() != null) completionBuilder.append(call.completion()).append("\n");
            }
            String prompt = promptBuilder.toString().trim();
            String completion = completionBuilder.toString().trim();

            double rating = 0.0;
            String response = "";
            if (!prompt.isEmpty() && !completion.isEmpty()) {
                String judgePrompt = promptTemplate
                    .replace("{prompt}", prompt)
                    .replace("{completion}", completion);
                try {
                    response = agentInvoker.invoke("{}", judgePrompt);
                    rating = extractRating(response);
                } catch (Exception e) {
                    // Fall back
                }
            }

            score = rating / 10.0;
            passed = score >= threshold;
            details = Map.of(
                "threshold", threshold,
                "rating", rating,
                "response", response
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

    private double extractRating(@NonNull String content) {
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(content);
        if (matcher.find()) {
            try {
                double rating = Double.parseDouble(matcher.group(1));
                return Math.max(0.0, Math.min(10.0, rating));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0.0;
    }

    public @NonNull List<RunEvaluation> getRunEvaluations(@NonNull String runId) {
        return runEvaluationRepository.findByRunId(runId);
    }

    public record EvaluatorWithScore(
        @NonNull Evaluator evaluator,
        double score24h
    ) {}
}
