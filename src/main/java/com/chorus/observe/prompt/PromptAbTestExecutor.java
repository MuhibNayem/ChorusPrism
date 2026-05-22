package com.chorus.observe.prompt;

import com.chorus.engine.evals.*;
import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import com.chorus.observe.service.AgentInvoker;
import com.chorus.observe.lock.DistributedLock;
import com.chorus.observe.lock.DistributedLockRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Enterprise-grade prompt A/B test executor.
 * <p>
 * Execution pipeline:
 * <ol>
 *   <li>Acquire distributed lock on test ID (prevents concurrent execution across JVMs)</li>
 *   <li>Resolve both prompt versions and the dataset</li>
 *   <li>Inject prompt A into the agent config and run {@link ParallelEvalRunner}</li>
 *   <li>Inject prompt B into the agent config and run {@link ParallelEvalRunner}</li>
 *   <li>Compute Welch's t-test on per-case scores</li>
 *   <li>Determine winner based on p-value, pass rate, and average score</li>
 *   <li>Persist results and transition test status</li>
 * </ol>
 */
public class PromptAbTestExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(PromptAbTestExecutor.class);

    private final PromptVersionRepository promptVersionRepository;
    private final PromptAbTestRepository promptAbTestRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetItemRepository datasetItemRepository;
    private final EvalResultRepository evalResultRepository;
    private final AgentInvoker agentInvoker;
    private final ObjectMapper mapper;
    private final DistributedLockRegistry lockRegistry;

    public PromptAbTestExecutor(
            @NonNull PromptVersionRepository promptVersionRepository,
            @NonNull PromptAbTestRepository promptAbTestRepository,
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository,
            @NonNull EvalResultRepository evalResultRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper) {
        this(promptVersionRepository, promptAbTestRepository, datasetRepository, datasetItemRepository,
            evalResultRepository, agentInvoker, mapper, null);
    }

    public PromptAbTestExecutor(
            @NonNull PromptVersionRepository promptVersionRepository,
            @NonNull PromptAbTestRepository promptAbTestRepository,
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository,
            @NonNull EvalResultRepository evalResultRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper,
            @Nullable DistributedLockRegistry lockRegistry) {
        this.promptVersionRepository = Objects.requireNonNull(promptVersionRepository);
        this.promptAbTestRepository = Objects.requireNonNull(promptAbTestRepository);
        this.datasetRepository = Objects.requireNonNull(datasetRepository);
        this.datasetItemRepository = Objects.requireNonNull(datasetItemRepository);
        this.evalResultRepository = Objects.requireNonNull(evalResultRepository);
        this.agentInvoker = Objects.requireNonNull(agentInvoker);
        this.mapper = Objects.requireNonNull(mapper);
        this.lockRegistry = lockRegistry;
    }

    /**
     * Execute an A/B test and return the result.
     *
     * @param testId the A/B test ID
     * @return the execution result
     * @throws IllegalArgumentException if the test, prompts, or dataset are missing
     * @throws IllegalStateException    if the test is already running or completed
     */
    public @NonNull AbTestResult execute(@NonNull String testId) {
        // Distributed lock to prevent concurrent execution across JVMs
        java.util.Optional<DistributedLock.LockToken> lockOpt = java.util.Optional.empty();
        if (lockRegistry != null) {
            DistributedLock lock = lockRegistry.getLock("ab-test-" + testId);
            try {
                lockOpt = lock.tryLock(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while acquiring lock for A/B test: " + testId);
            }
            if (lockOpt.isEmpty()) {
                throw new IllegalStateException("A/B test is already running (locked) on another node: " + testId);
            }
        }

        try {
            return doExecute(testId);
        } finally {
            lockOpt.ifPresent(token -> {
                try {
                    lockRegistry.getLock("ab-test-" + testId).unlock(token);
                } catch (Exception e) {
                    LOG.warn("Failed to release A/B test lock for {}", testId, e);
                }
            });
        }
    }

    private @NonNull AbTestResult doExecute(@NonNull String testId) {
        PromptAbTest test = promptAbTestRepository.findById(testId)
            .orElseThrow(() -> new IllegalArgumentException("A/B test not found: " + testId));

        if (test.status() == PromptAbTest.Status.RUNNING) {
            throw new IllegalStateException("A/B test is already running: " + testId);
        }
        if (test.status() == PromptAbTest.Status.COMPLETED) {
            throw new IllegalStateException("A/B test already completed: " + testId);
        }

        PromptVersion promptA = promptVersionRepository.findById(test.promptAId())
            .orElseThrow(() -> new IllegalArgumentException("Prompt A not found: " + test.promptAId()));
        PromptVersion promptB = promptVersionRepository.findById(test.promptBId())
            .orElseThrow(() -> new IllegalArgumentException("Prompt B not found: " + test.promptBId()));

        if (test.datasetId() == null) {
            throw new IllegalArgumentException("A/B test has no dataset configured");
        }

        List<DatasetItem> items = datasetItemRepository.findByDatasetId(test.datasetId());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Dataset is empty: " + test.datasetId());
        }

        // Transition to RUNNING
        promptAbTestRepository.save(new PromptAbTest(
            test.testId(), test.datasetId(), test.promptAId(), test.promptBId(),
            PromptAbTest.Status.RUNNING, null, null, test.summaryMetrics(),
            test.createdAt(), null
        ));

        try {
            List<EvalCase> cases = items.stream()
                .map(i -> new EvalCase(i.itemId(), i.input(), i.expectedOutput() != null ? i.expectedOutput() : "", i.metadata()))
                .toList();
            EvalDataset dataset = EvalDataset.of(test.testId(), cases);

            // Resolve scorer from test config or default to exact_match
            EvalScorer scorer = resolveScorer(test.summaryMetrics());
            int parallelism = resolveParallelism(test.summaryMetrics());

            // Run prompt A
            LOG.info("A/B test {}: running prompt A ({} cases, scorer={}, parallelism={})",
                testId, cases.size(), scorer.getClass().getSimpleName(), parallelism);
            EvalReport reportA = runEval(dataset, promptA, "A", scorer, parallelism);

            // Run prompt B
            LOG.info("A/B test {}: running prompt B ({} cases, scorer={}, parallelism={})",
                testId, cases.size(), scorer.getClass().getSimpleName(), parallelism);
            EvalReport reportB = runEval(dataset, promptB, "B", scorer, parallelism);

            // Statistical test
            List<Double> scoresA = reportA.results().stream().map(EvalResult::score).toList();
            List<Double> scoresB = reportB.results().stream().map(EvalResult::score).toList();
            WelchTTest.Result ttest = WelchTTest.test(scoresA, scoresB, 0.05);

            // Determine winner
            String winnerId = determineWinner(reportA, reportB, ttest);

            Map<String, Object> summary = new HashMap<>();
            summary.put("passRateA", reportA.passRate());
            summary.put("passRateB", reportB.passRate());
            summary.put("avgScoreA", reportA.avgScore());
            summary.put("avgScoreB", reportB.avgScore());
            summary.put("tStatistic", ttest.tStatistic());
            summary.put("pValue", ttest.pValue());
            summary.put("significant", ttest.significant());
            summary.put("winnerId", winnerId);

            promptAbTestRepository.save(new PromptAbTest(
                test.testId(), test.datasetId(), test.promptAId(), test.promptBId(),
                PromptAbTest.Status.COMPLETED, winnerId, ttest.pValue(),
                Map.copyOf(summary), test.createdAt(), Instant.now()
            ));

            LOG.info("A/B test {} completed. Winner: {}, p-value: {}", testId, winnerId, ttest.pValue());
            return new AbTestResult(testId, reportA, reportB, ttest, winnerId);

        } catch (Exception e) {
            LOG.error("A/B test {} failed", testId, e);
            promptAbTestRepository.save(new PromptAbTest(
                test.testId(), test.datasetId(), test.promptAId(), test.promptBId(),
                PromptAbTest.Status.FAILED, null, null,
                Map.of("error", e.getMessage()), test.createdAt(), Instant.now()
            ));
            throw new RuntimeException("A/B test execution failed: " + e.getMessage(), e);
        }
    }

    private @NonNull EvalReport runEval(@NonNull EvalDataset dataset, @NonNull PromptVersion prompt,
                                        @NonNull String label, @NonNull EvalScorer scorer, int parallelism) {
        Function<String, String> runner = input -> {
            try {
                Map<String, Object> config = new HashMap<>(Map.of("model", prompt.model() != null ? prompt.model() : "unknown"));
                config.put("prompt", prompt.content());
                config.put("temperature", prompt.temperature() != null ? prompt.temperature() : 0.7);
                config.put("maxTokens", prompt.maxTokens() != null ? prompt.maxTokens() : 1024);
                String configJson = mapper.writeValueAsString(config);
                return agentInvoker.invoke(configJson, input);
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        };

        ParallelEvalRunner parallelRunner = new ParallelEvalRunner("ab-" + label, parallelism);
        return parallelRunner.run(dataset, runner, scorer);
    }

    private @NonNull EvalScorer resolveScorer(@Nullable Map<String, Object> config) {
        if (config == null) return new ExactMatchScorer();
        Object scorerName = config.get("scorer");
        if (scorerName == null) return new ExactMatchScorer();
        return switch (scorerName.toString()) {
            case "contains" -> new ContainsScorer();
            case "exact_match" -> new ExactMatchScorer();
            default -> new ExactMatchScorer();
        };
    }

    private int resolveParallelism(@Nullable Map<String, Object> config) {
        if (config == null) return Math.min(4, Runtime.getRuntime().availableProcessors());
        Object p = config.get("parallelism");
        if (p instanceof Number n) {
            return Math.max(1, Math.min(n.intValue(), Runtime.getRuntime().availableProcessors()));
        }
        return Math.min(4, Runtime.getRuntime().availableProcessors());
    }

    private @Nullable String determineWinner(@NonNull EvalReport reportA, @NonNull EvalReport reportB, WelchTTest.@NonNull Result ttest) {
        if (!ttest.significant()) {
            return null; // No statistically significant difference
        }
        return reportA.avgScore() > reportB.avgScore() ? "A" : "B";
    }

    public record AbTestResult(
        @NonNull String testId,
        @NonNull EvalReport reportA,
        @NonNull EvalReport reportB,
        WelchTTest.@NonNull Result ttest,
        @Nullable String winnerLabel
    ) {}
}
