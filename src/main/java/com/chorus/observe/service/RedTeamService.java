package com.chorus.observe.service;

import com.chorus.engine.guardrails.Guardrail;
import com.chorus.engine.guardrails.GuardrailResult;
import com.chorus.engine.guardrails.TieredGuardrailEngine;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.RedTeamResult;
import com.chorus.observe.model.RedTeamRun;
import com.chorus.observe.model.RedTeamScenario;
import com.chorus.observe.persistence.RedTeamResultRepository;
import com.chorus.observe.persistence.RedTeamRunRepository;
import com.chorus.observe.persistence.RedTeamScenarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Service for red teaming: adversarial scenario execution against guardrails.
 * <p>
 * When a {@link TieredGuardrailEngine} is configured, each agent output is evaluated
 * through the engine's output guardrails. A <em>bypass</em> occurs when the guardrails
 * allow content that should have been blocked.
 * <p>
 * Without a guardrail engine, runs proceed but are flagged with
 * {@code guardrailStatus = NO_ENGINE} rather than claiming bypass/block heuristically.
 */
public class RedTeamService {

    private static final Logger LOG = LoggerFactory.getLogger(RedTeamService.class);

    private final RedTeamScenarioRepository scenarioRepository;
    private final RedTeamRunRepository runRepository;
    private final RedTeamResultRepository resultRepository;
    private final AgentInvoker agentInvoker;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final Set<String> cancelledRuns = ConcurrentHashMap.newKeySet();
    private final MetricsService metricsService;
    private final TieredGuardrailEngine guardrailEngine;

    public RedTeamService(
            @NonNull RedTeamScenarioRepository scenarioRepository,
            @NonNull RedTeamRunRepository runRepository,
            @NonNull RedTeamResultRepository resultRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper) {
        this(scenarioRepository, runRepository, resultRepository, agentInvoker, mapper, null, null);
    }

    private static final Duration STALE_RUN_THRESHOLD = Duration.ofMinutes(30);

    public RedTeamService(
            @NonNull RedTeamScenarioRepository scenarioRepository,
            @NonNull RedTeamRunRepository runRepository,
            @NonNull RedTeamResultRepository resultRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper,
            @Nullable MetricsService metricsService,
            @Nullable TieredGuardrailEngine guardrailEngine) {
        this.scenarioRepository = Objects.requireNonNull(scenarioRepository);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.agentInvoker = Objects.requireNonNull(agentInvoker);
        this.mapper = Objects.requireNonNull(mapper);
        this.metricsService = metricsService;
        this.guardrailEngine = guardrailEngine;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PostConstruct
    public void recoverStaleRuns() {
        try {
            List<RedTeamRun> running = runRepository.findByStatus(RedTeamRun.Status.RUNNING);
            Instant cutoff = Instant.now().minus(STALE_RUN_THRESHOLD);
            int recovered = 0;
            for (RedTeamRun run : running) {
                if (run.startedAt() != null && run.startedAt().isBefore(cutoff)) {
                    LOG.warn("Recovering stale red team run {} (started at {}, threshold {})",
                        run.redTeamRunId(), run.startedAt(), STALE_RUN_THRESHOLD);
                    runRepository.save(new RedTeamRun(
                        run.redTeamRunId(), run.agentConfig(), RedTeamRun.Status.FAILED,
                        run.totalScenarios(), run.bypassedCount(), run.blockedCount(), run.progressPercent(),
                        Map.of("error", "Recovered from crash: run exceeded " + STALE_RUN_THRESHOLD.toMinutes() + " minutes"),
                        run.startedAt(), Instant.now(), run.createdAt()
                    ));
                    recovered++;
                }
            }
            if (recovered > 0) {
                LOG.info("Recovered {} stale red team run(s)", recovered);
            }
        } catch (Exception e) {
            LOG.error("Red team run recovery failed", e);
        }
    }

    public @NonNull RedTeamScenario createScenario(@NonNull String name, @NonNull String category, @NonNull String attackPrompt, @Nullable String expectedBehavior, RedTeamScenario.Severity severity) {
        String scenarioId = "rt-scenario-" + UUID.randomUUID().toString().substring(0, 8);
        RedTeamScenario scenario = new RedTeamScenario(scenarioId, name, category, attackPrompt, expectedBehavior, severity, Map.of(), Instant.now());
        scenarioRepository.save(scenario);
        return scenario;
    }

    public @NonNull Optional<RedTeamScenario> getScenario(@NonNull String scenarioId) {
        return scenarioRepository.findById(scenarioId);
    }

    public @NonNull List<RedTeamScenario> listScenarios() {
        return scenarioRepository.findAll();
    }

    public @NonNull PagedResult<RedTeamScenario> listScenarios(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(scenarioRepository.findAll(size, offset), scenarioRepository.count(), page, size);
    }

    public @NonNull List<RedTeamScenario> listScenariosByCategory(@NonNull String category) {
        return scenarioRepository.findByCategory(category);
    }

    public @NonNull PagedResult<RedTeamScenario> listScenariosByCategory(@NonNull String category, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(scenarioRepository.findByCategory(category, size, offset), scenarioRepository.countByCategory(category), page, size);
    }

    public @NonNull RedTeamRun submitRedTeamRun(@NonNull Map<String, Object> agentConfig) {
        String redTeamRunId = "rt-run-" + UUID.randomUUID().toString().substring(0, 8);
        RedTeamRun run = new RedTeamRun(redTeamRunId, agentConfig, RedTeamRun.Status.PENDING, 0, 0, 0, 0, Map.of(), null, null, Instant.now());
        runRepository.save(run);
        if (metricsService != null) {
            metricsService.incrementRedTeamRunsTotal();
        }
        return run;
    }

    public void startRedTeamRun(@NonNull String redTeamRunId) {
        Optional<RedTeamRun> opt = runRepository.findById(redTeamRunId);
        if (opt.isEmpty()) return;
        RedTeamRun run = opt.get();
        if (run.status() != RedTeamRun.Status.PENDING) return;

        List<RedTeamScenario> scenarios = scenarioRepository.findAll();
        runRepository.save(new RedTeamRun(
            run.redTeamRunId(), run.agentConfig(), RedTeamRun.Status.RUNNING,
            scenarios.size(), 0, 0, 0, run.summaryMetrics(), Instant.now(), null, run.createdAt()
        ));

        CompletableFuture.runAsync(() -> executeRedTeamRun(redTeamRunId, scenarios), executor);
    }

    private void executeRedTeamRun(@NonNull String redTeamRunId, @NonNull List<RedTeamScenario> scenarios) {
        try {
            Optional<RedTeamRun> opt = runRepository.findById(redTeamRunId);
            if (opt.isEmpty()) return;
            RedTeamRun run = opt.get();
            String agentConfigJson = mapper.writeValueAsString(run.agentConfig());

            int bypassed = 0;
            int blocked = 0;
            int noEngine = 0;
            int saveInterval = Math.max(1, scenarios.size() / 10);

            Guardrail.GuardrailContext guardrailContext = new Guardrail.GuardrailContext(
                redTeamRunId, "red-team", "output"
            );

            for (int i = 0; i < scenarios.size(); i++) {
                RedTeamScenario scenario = scenarios.get(i);
                if (cancelledRuns.remove(redTeamRunId)) {
                    runRepository.save(new RedTeamRun(
                        run.redTeamRunId(), run.agentConfig(), RedTeamRun.Status.CANCELLED,
                        scenarios.size(), bypassed, blocked, (int) ((i * 100.0) / scenarios.size()),
                        Map.of("error", "Cancelled by user", "noEngineCount", noEngine), run.startedAt(), Instant.now(), run.createdAt()
                    ));
                    return;
                }

                long start = System.currentTimeMillis();
                String output = agentInvoker.invoke(agentConfigJson, scenario.attackPrompt());
                long latency = System.currentTimeMillis() - start;

                GuardrailEvaluation eval = evaluateOutput(output, guardrailContext);

                boolean bypassedFlag;
                String guardrailStatus;
                if (eval == null) {
                    bypassedFlag = false;
                    guardrailStatus = "NO_ENGINE";
                    noEngine++;
                } else {
                    bypassedFlag = eval.allowed;
                    guardrailStatus = eval.allowed ? "BYPASSED" : "BLOCKED";
                    if (eval.allowed) {
                        bypassed++;
                    } else {
                        blocked++;
                    }
                }

                Map<String, Object> metadata = new java.util.HashMap<>();
                metadata.put("action", guardrailStatus);
                metadata.put("agentLatencyMs", latency);
                if (eval != null) {
                    metadata.put("guardrailLatencyMs", eval.latencyMs);
                    metadata.put("triggeredGuardrails", eval.triggeredNames);
                    metadata.put("maxConfidence", eval.maxConfidence);
                }

                RedTeamResult result = new RedTeamResult(
                    "rt-res-" + UUID.randomUUID().toString().substring(0, 8),
                    redTeamRunId, scenario.scenarioId(), output,
                    Map.copyOf(metadata),
                    bypassedFlag, scenario.severity(), latency, Instant.now()
                );
                resultRepository.save(result);

                if ((i + 1) % saveInterval == 0) {
                    int progress = (int) (((i + 1) * 100.0) / scenarios.size());
                    runRepository.save(new RedTeamRun(
                        run.redTeamRunId(), run.agentConfig(), RedTeamRun.Status.RUNNING,
                        scenarios.size(), bypassed, blocked, progress, run.summaryMetrics(),
                        run.startedAt(), null, run.createdAt()
                    ));
                }
            }

            Map<String, Object> summary = new java.util.HashMap<>();
            summary.put("totalScenarios", scenarios.size());
            summary.put("bypassed", bypassed);
            summary.put("blocked", blocked);
            summary.put("noEngine", noEngine);
            summary.put("bypassRate", scenarios.isEmpty() ? 0.0 : (double) bypassed / scenarios.size());
            summary.put("guardrailConfigured", guardrailEngine != null);

            runRepository.save(new RedTeamRun(
                run.redTeamRunId(), run.agentConfig(), RedTeamRun.Status.COMPLETED,
                scenarios.size(), bypassed, blocked, 100, Map.copyOf(summary), run.startedAt(), Instant.now(), run.createdAt()
            ));
        } catch (Exception e) {
            LOG.error("Red team run {} failed", redTeamRunId, e);
            Optional<RedTeamRun> opt = runRepository.findById(redTeamRunId);
            if (opt.isPresent()) {
                RedTeamRun run = opt.get();
                runRepository.save(new RedTeamRun(
                    run.redTeamRunId(), run.agentConfig(), RedTeamRun.Status.FAILED,
                    run.totalScenarios(), run.bypassedCount(), run.blockedCount(), 0,
                    Map.of("error", e.getMessage()), run.startedAt(), Instant.now(), run.createdAt()
                ));
            }
        }
    }

    private @Nullable GuardrailEvaluation evaluateOutput(@NonNull String output, Guardrail.GuardrailContext context) {
        if (guardrailEngine == null) {
            return null;
        }
        Instant start = Instant.now();
        TieredGuardrailEngine.EvaluationResult result = guardrailEngine.evaluateOutput(output, context);
        long latencyMs = Duration.between(start, Instant.now()).toMillis();

        List<String> triggered = result.details().stream()
            .filter(r -> !r.allowed() || r.action() != GuardrailResult.Action.ALLOW)
            .map(GuardrailResult::guardrailName)
            .toList();

        double maxConfidence = result.details().stream()
            .mapToDouble(GuardrailResult::confidence)
            .max()
            .orElse(0.0);

        return new GuardrailEvaluation(result.allowed(), triggered, maxConfidence, latencyMs);
    }

    private record GuardrailEvaluation(
        boolean allowed,
        @NonNull List<String> triggeredNames,
        double maxConfidence,
        long latencyMs
    ) {}

    public @NonNull Optional<RedTeamRun> getRedTeamRun(@NonNull String redTeamRunId) {
        return runRepository.findById(redTeamRunId);
    }

    public @NonNull List<RedTeamRun> listRedTeamRuns() {
        return runRepository.findAll();
    }

    public @NonNull PagedResult<RedTeamRun> listRedTeamRuns(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(runRepository.findAll(size, offset), runRepository.count(), page, size);
    }

    public @NonNull List<RedTeamResult> getRedTeamResults(@NonNull String redTeamRunId) {
        return resultRepository.findByRedTeamRunId(redTeamRunId);
    }

    public @NonNull PagedResult<RedTeamResult> getRedTeamResults(@NonNull String redTeamRunId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(resultRepository.findByRedTeamRunId(redTeamRunId, size, offset), resultRepository.countByRedTeamRunId(redTeamRunId), page, size);
    }

    public void cancelRedTeamRun(@NonNull String redTeamRunId) {
        cancelledRuns.add(redTeamRunId);
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
    }
}
