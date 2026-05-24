package com.chorus.observe.service;

import com.chorus.engine.evals.*;
import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for evaluation execution, result storage, and regression detection.
 * <p>
 * Scorers supported:
 * <ul>
 *   <li>{@code exact_match} — character-for-character equality</li>
 *   <li>{@code contains} — case-insensitive substring match</li>
 *   <li>{@code llm_judge} — LLM-as-judge via {@link AgentInvoker}</li>
 * </ul>
 * <p>
 * N-run scoring: each eval case is executed a minimum of N times (configurable via
 * {@code minRuns}, default 1). The reported score is the median of all runs,
 * preventing single-run LLM non-determinism from causing false failures.
 * <p>
 * Crash recovery: on startup, any eval runs in {@code RUNNING} state for more than
 * 30 minutes are automatically marked {@code FAILED} with a recovery note.
 */
public class EvalService {

    private static final Logger LOG = LoggerFactory.getLogger(EvalService.class);
    private static final Duration STALE_RUN_THRESHOLD = Duration.ofMinutes(30);

    private final DatasetRepository datasetRepository;
    private final DatasetItemRepository datasetItemRepository;
    private final EvalRunRepository evalRunRepository;
    private final EvalResultRepository evalResultRepository;
    private final EvalResultRunRepository evalResultRunRepository;
    private final AgentInvoker agentInvoker;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final Set<String> cancelledRuns = ConcurrentHashMap.newKeySet();
    private final MetricsService metricsService;

    public EvalService(
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository,
            @NonNull EvalRunRepository evalRunRepository,
            @NonNull EvalResultRepository evalResultRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper) {
        this(datasetRepository, datasetItemRepository, evalRunRepository, evalResultRepository, null, agentInvoker, mapper, null);
    }

    public EvalService(
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository,
            @NonNull EvalRunRepository evalRunRepository,
            @NonNull EvalResultRepository evalResultRepository,
            @Nullable EvalResultRunRepository evalResultRunRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper,
            @Nullable MetricsService metricsService) {
        this.datasetRepository = Objects.requireNonNull(datasetRepository);
        this.datasetItemRepository = Objects.requireNonNull(datasetItemRepository);
        this.evalRunRepository = Objects.requireNonNull(evalRunRepository);
        this.evalResultRepository = Objects.requireNonNull(evalResultRepository);
        this.evalResultRunRepository = evalResultRunRepository;
        this.agentInvoker = Objects.requireNonNull(agentInvoker);
        this.mapper = Objects.requireNonNull(mapper);
        this.metricsService = metricsService;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PostConstruct
    public void recoverStaleRuns() {
        try {
            List<EvalRun> running = evalRunRepository.findByStatus(EvalRun.Status.RUNNING);
            Instant cutoff = Instant.now().minus(STALE_RUN_THRESHOLD);
            int recovered = 0;
            for (EvalRun run : running) {
                if (run.startedAt() != null && run.startedAt().isBefore(cutoff)) {
                    LOG.warn("Recovering stale eval run {} (started at {}, threshold {})",
                        run.evalRunId(), run.startedAt(), STALE_RUN_THRESHOLD);
                    evalRunRepository.save(new EvalRun(
                        run.evalRunId(), run.datasetId(), run.name(),
                        run.agentConfig(), run.scorerConfig(), run.parallelism(), run.minRuns(),
                        EvalRun.Status.FAILED, run.progressPercent(),
                        Map.of("error", "Recovered from crash: run exceeded " + STALE_RUN_THRESHOLD.toMinutes() + " minutes"),
                        run.startedAt(), Instant.now(), run.createdAt()
                    ));
                    recovered++;
                }
            }
            if (recovered > 0) {
                LOG.info("Recovered {} stale eval run(s)", recovered);
            }
        } catch (Exception e) {
            LOG.error("Eval run recovery failed", e);
        }
    }

    @Timed(value = "eval.submit", description = "Time spent submitting an eval run")
    @Counted(value = "eval.submit.count", description = "Total number of eval run submissions")
    public @NonNull EvalRun submitEvalRun(@NonNull String datasetId, @Nullable String name,
                                          @NonNull Map<String, Object> agentConfig,
                                          @NonNull Map<String, Object> scorerConfig, int parallelism) {
        return submitEvalRun(datasetId, name, agentConfig, scorerConfig, parallelism, 1);
    }

    public @NonNull EvalRun submitEvalRun(@NonNull String datasetId, @Nullable String name,
                                          @NonNull Map<String, Object> agentConfig,
                                          @NonNull Map<String, Object> scorerConfig,
                                          int parallelism, int minRuns) {
        if (minRuns < 1) minRuns = 1;
        String evalRunId = "eval-" + UUID.randomUUID().toString().substring(0, 8);
        EvalRun evalRun = new EvalRun(evalRunId, datasetId, name, agentConfig, scorerConfig, parallelism, minRuns,
            EvalRun.Status.PENDING, 0, Map.of(), null, null, Instant.now());
        evalRunRepository.save(evalRun);
        if (metricsService != null) {
            metricsService.incrementEvalRunsTotal();
        }
        return evalRun;
    }

    @Timed(value = "eval.start", description = "Time spent starting an eval run")
    @Counted(value = "eval.start.count", description = "Total number of eval run starts")
    @Transactional
    public void startEvalRun(@NonNull String evalRunId) {
        Optional<EvalRun> opt = evalRunRepository.findById(evalRunId);
        if (opt.isEmpty()) return;
        EvalRun evalRun = opt.get();
        if (evalRun.status() != EvalRun.Status.PENDING) return;

        evalRunRepository.save(new EvalRun(
            evalRun.evalRunId(), evalRun.datasetId(), evalRun.name(),
            evalRun.agentConfig(), evalRun.scorerConfig(), evalRun.parallelism(), evalRun.minRuns(),
            EvalRun.Status.RUNNING, 0, evalRun.summaryMetrics(),
            Instant.now(), evalRun.finishedAt(), evalRun.createdAt()
        ));

        long startTime = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                executeEvalRun(evalRunId);
            } finally {
                if (metricsService != null) {
                    metricsService.recordEvalRunDuration(System.currentTimeMillis() - startTime);
                }
            }
        }, executor);
    }

    private void executeEvalRun(@NonNull String evalRunId) {
        try {
            Optional<EvalRun> opt = evalRunRepository.findById(evalRunId);
            if (opt.isEmpty()) return;
            EvalRun evalRun = opt.get();

            List<DatasetItem> items = datasetItemRepository.findByDatasetId(evalRun.datasetId());
            if (items.isEmpty()) {
                completeEvalRun(evalRunId, EvalRun.Status.FAILED, Map.of("error", "Dataset is empty"));
                return;
            }

            String agentConfigJson = mapper.writeValueAsString(evalRun.agentConfig());
            Function<String, String> runner = input -> agentInvoker.invoke(agentConfigJson, input);

            List<EvalCase> cases = items.stream()
                .map(i -> new EvalCase(i.itemId(), i.input(), i.expectedOutput() != null ? i.expectedOutput() : "", i.metadata()))
                .toList();
            EvalDataset dataset = EvalDataset.of(evalRun.name() != null ? evalRun.name() : "eval", cases);

            EvalScorer scorer = resolveScorer(evalRun.scorerConfig());
            int minRuns = Math.max(1, evalRun.minRuns());

            AtomicInteger processed = new AtomicInteger(0);
            int totalSteps = cases.size() * minRuns;
            int saveInterval = Math.max(1, totalSteps / 10);

            int parallelism = Math.max(1, evalRun.parallelism());
            Semaphore semaphore = new Semaphore(parallelism);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (EvalCase evalCase : cases) {
                if (cancelledRuns.contains(evalRunId)) {
                    completeEvalRun(evalRunId, EvalRun.Status.CANCELLED, Map.of("error", "Cancelled by user"));
                    return;
                }

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Eval interrupted", e);
                    }
                    try {
                        if (cancelledRuns.contains(evalRunId)) {
                            return;
                        }

                        List<EvalResult> nRunResults = new ArrayList<>();
                        for (int runNum = 1; runNum <= minRuns; runNum++) {
                            String actualOutput = runner.apply(evalCase.input());
                            EvalResult singleResult = scorer.score(evalCase, actualOutput);
                            nRunResults.add(singleResult);
                            int p = processed.incrementAndGet();

                            if (p % saveInterval == 0) {
                                int progress = (int) ((p * 100.0) / totalSteps);
                                evalRunRepository.save(new EvalRun(
                                    evalRun.evalRunId(), evalRun.datasetId(), evalRun.name(),
                                    evalRun.agentConfig(), evalRun.scorerConfig(), evalRun.parallelism(), evalRun.minRuns(),
                                    EvalRun.Status.RUNNING, progress, evalRun.summaryMetrics(),
                                    evalRun.startedAt(), evalRun.finishedAt(), evalRun.createdAt()
                                ));
                            }
                        }

                        double medianScore = computeMedian(nRunResults.stream().mapToDouble(EvalResult::score).sorted().toArray());
                        boolean majorityPassed = nRunResults.stream().filter(EvalResult::passed).count() > nRunResults.size() / 2;
                        EvalResult representative = nRunResults.get(nRunResults.size() / 2);

                        EvalResultRecord record = new EvalResultRecord(
                            "res-" + UUID.randomUUID().toString().substring(0, 8),
                            evalRunId, evalCase.id(), null, null,
                            representative.actualOutput(), medianScore, majorityPassed,
                            0L, representative.reasoning(), Instant.now()
                        );
                        evalResultRepository.save(record);

                        if (evalResultRunRepository != null && minRuns > 1) {
                            for (int runNum = 1; runNum <= nRunResults.size(); runNum++) {
                                EvalResult r = nRunResults.get(runNum - 1);
                                EvalResultRun resultRun = new EvalResultRun(
                                    "err-" + UUID.randomUUID().toString().substring(0, 8),
                                    record.resultId(), runNum, r.score(), r.passed(),
                                    r.actualOutput(), r.reasoning(), 0L, Instant.now()
                                );
                                evalResultRunRepository.save(resultRun);
                            }
                        }
                    } finally {
                        semaphore.release();
                    }
                }, executor);
                futures.add(future);
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                LOG.error("Eval run {} parallel execution failed", evalRunId, e);
                completeEvalRun(evalRunId, EvalRun.Status.FAILED, Map.of("error", e.getMessage()));
                return;
            }

            if (cancelledRuns.remove(evalRunId)) {
                completeEvalRun(evalRunId, EvalRun.Status.CANCELLED, Map.of("error", "Cancelled by user"));
                return;
            }

            List<EvalResultRecord> allRecords = evalResultRepository.findByEvalRunId(evalRunId);
            long passedCount = allRecords.stream().filter(EvalResultRecord::passed).count();
            double avgScore = allRecords.stream().mapToDouble(EvalResultRecord::score).average().orElse(0.0);

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalCases", cases.size());
            summary.put("passed", passedCount);
            summary.put("passRate", cases.isEmpty() ? 0.0 : (double) passedCount / cases.size());
            summary.put("avgScore", avgScore);
            summary.put("minRuns", minRuns);
            completeEvalRun(evalRunId, EvalRun.Status.COMPLETED, summary);
        } catch (Exception e) {
            LOG.error("Eval run {} failed", evalRunId, e);
            completeEvalRun(evalRunId, EvalRun.Status.FAILED, Map.of("error", e.getMessage()));
        }
    }

    private double computeMedian(double[] sortedScores) {
        int n = sortedScores.length;
        if (n == 0) return 0.0;
        if (n % 2 == 1) return sortedScores[n / 2];
        return (sortedScores[n / 2 - 1] + sortedScores[n / 2]) / 2.0;
    }

    private void completeEvalRun(@NonNull String evalRunId, EvalRun.Status status, @NonNull Map<String, Object> summary) {
        Optional<EvalRun> opt = evalRunRepository.findById(evalRunId);
        if (opt.isEmpty()) return;
        EvalRun evalRun = opt.get();
        int progress = status == EvalRun.Status.COMPLETED ? 100 : (status == EvalRun.Status.FAILED ? 0 : evalRun.progressPercent());
        evalRunRepository.save(new EvalRun(
            evalRun.evalRunId(), evalRun.datasetId(), evalRun.name(),
            evalRun.agentConfig(), evalRun.scorerConfig(), evalRun.parallelism(), evalRun.minRuns(),
            status, progress, summary, evalRun.startedAt(), Instant.now(), evalRun.createdAt()
        ));
    }

    private @NonNull EvalScorer resolveScorer(@NonNull Map<String, Object> scorerConfig) {
        List<String> names;
        try {
            names = mapper.convertValue(scorerConfig.getOrDefault("scorers", List.of("exact_match")), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            LOG.warn("Failed to parse scorer config, defaulting to exact_match", e);
            names = List.of("exact_match");
        }
        String primary = names.isEmpty() ? "exact_match" : names.get(0);

        return switch (primary) {
            case "exact_match" -> new ExactMatchScorer();
            case "contains" -> new ContainsScorer();
            case "llm_judge" -> {
                double threshold = parseDouble(scorerConfig.get("threshold"), 0.7);
                yield new AgentInvokerJudgeScorer(agentInvoker, threshold);
            }
            default -> {
                LOG.warn("Unknown scorer '{}', falling back to exact_match", primary);
                yield new ExactMatchScorer();
            }
        };
    }

    private double parseDouble(@Nullable Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public @NonNull Optional<EvalRun> getEvalRun(@NonNull String evalRunId) {
        return evalRunRepository.findById(evalRunId);
    }

    public @NonNull List<EvalRun> listEvalRuns() {
        return evalRunRepository.findAll();
    }

    public @NonNull PagedResult<EvalRun> listEvalRuns(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(evalRunRepository.findAll(size, offset), evalRunRepository.count(), page, size);
    }

    public @NonNull List<EvalRun> listEvalRunsByDataset(@NonNull String datasetId) {
        return evalRunRepository.findByDatasetId(datasetId);
    }

    public @NonNull PagedResult<EvalRun> listEvalRunsByDataset(@NonNull String datasetId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(evalRunRepository.findByDatasetId(datasetId, size, offset), evalRunRepository.countByDatasetId(datasetId), page, size);
    }

    public @NonNull List<EvalResultRecord> getEvalResults(@NonNull String evalRunId) {
        return evalResultRepository.findByEvalRunId(evalRunId);
    }

    public @NonNull PagedResult<EvalResultRecord> getEvalResults(@NonNull String evalRunId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(evalResultRepository.findByEvalRunId(evalRunId, size, offset), evalResultRepository.countByEvalRunId(evalRunId), page, size);
    }

    public void cancelEvalRun(@NonNull String evalRunId) {
        cancelledRuns.add(evalRunId);
    }

    public @NonNull RegressionReport compareRuns(@NonNull String evalRunIdA, @NonNull String evalRunIdB) {
        List<EvalResultRecord> resultsA = evalResultRepository.findByEvalRunId(evalRunIdA);
        List<EvalResultRecord> resultsB = evalResultRepository.findByEvalRunId(evalRunIdB);

        Map<String, EvalResultRecord> mapA = resultsA.stream().collect(Collectors.toMap(EvalResultRecord::itemId, r -> r));
        Map<String, EvalResultRecord> mapB = resultsB.stream().collect(Collectors.toMap(EvalResultRecord::itemId, r -> r));

        int regressions = 0;
        int improvements = 0;
        int unchanged = 0;
        List<ItemDiff> changedItems = new ArrayList<>();

        for (String itemId : mapA.keySet()) {
            EvalResultRecord a = mapA.get(itemId);
            EvalResultRecord b = mapB.get(itemId);
            if (b == null) continue;

            if (a.passed() && !b.passed()) {
                regressions++;
                changedItems.add(new ItemDiff(itemId, a.score(), b.score(), false, a.actualOutput(), b.actualOutput()));
            } else if (!a.passed() && b.passed()) {
                improvements++;
                changedItems.add(new ItemDiff(itemId, a.score(), b.score(), true, a.actualOutput(), b.actualOutput()));
            } else {
                unchanged++;
            }
        }

        double avgA = resultsA.stream().mapToDouble(EvalResultRecord::score).average().orElse(0.0);
        double avgB = resultsB.stream().mapToDouble(EvalResultRecord::score).average().orElse(0.0);

        return new RegressionReport(regressions, improvements, unchanged, avgB - avgA, changedItems);
    }

    public record RegressionReport(
        int regressions,
        int improvements,
        int unchanged,
        double scoreDelta,
        @NonNull List<ItemDiff> changedItems
    ) {}

    public record ItemDiff(
        @NonNull String itemId,
        double scoreA,
        double scoreB,
        boolean improved,
        @NonNull String outputA,
        @NonNull String outputB
    ) {}

    @PreDestroy
    public void close() {
        executor.shutdown();
    }
}
