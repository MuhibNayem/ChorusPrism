package com.chorus.observe.service;

import com.chorus.observe.model.*;
import com.chorus.observe.persistence.MultiTurnRunRepository;
import com.chorus.observe.persistence.MultiTurnScenarioRepository;
import com.chorus.observe.persistence.MultiTurnTurnRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

/**
 * Service for multi-turn conversation testing.
 * <p>
 * Given a scenario with a sequence of turns, this service:
 * <ol>
 *   <li>Builds a conversation history from all previous turns</li>
 *   <li>Sends the current turn's message to the agent</li>
 *   <li>Evaluates the response against expected keywords</li>
 *   <li>Scores each turn and aggregates into a final report</li>
 * </ol>
 * <p>
 * The conversation history is maintained as a JSON array of {@code {role, content}}
 * objects and injected into the agent config under the {@code "messages"} key.
 */
public class MultiTurnTestService {

    private static final Logger LOG = LoggerFactory.getLogger(MultiTurnTestService.class);

    private final MultiTurnScenarioRepository scenarioRepository;
    private final MultiTurnRunRepository runRepository;
    private final MultiTurnTurnRepository turnRepository;
    private final AgentInvoker agentInvoker;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public MultiTurnTestService(
            @NonNull MultiTurnScenarioRepository scenarioRepository,
            @NonNull MultiTurnRunRepository runRepository,
            @NonNull MultiTurnTurnRepository turnRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper) {
        this.scenarioRepository = Objects.requireNonNull(scenarioRepository);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.turnRepository = Objects.requireNonNull(turnRepository);
        this.agentInvoker = Objects.requireNonNull(agentInvoker);
        this.mapper = Objects.requireNonNull(mapper);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public @NonNull MultiTurnScenario createScenario(@NonNull String name, @Nullable String description, @NonNull List<MultiTurnScenario.Turn> turns) {
        String scenarioId = "mts-" + UUID.randomUUID().toString().substring(0, 8);
        MultiTurnScenario scenario = new MultiTurnScenario(scenarioId, name, description, turns, Map.of(), Instant.now());
        scenarioRepository.save(scenario);
        return scenario;
    }

    public @NonNull Optional<MultiTurnScenario> getScenario(@NonNull String scenarioId) {
        return scenarioRepository.findById(scenarioId);
    }

    public @NonNull List<MultiTurnScenario> listScenarios() {
        return scenarioRepository.findAll();
    }

    public @NonNull MultiTurnRun submitRun(@NonNull String scenarioId, @NonNull Map<String, Object> agentConfig) {
        String runId = "mtr-" + UUID.randomUUID().toString().substring(0, 8);
        MultiTurnRun run = new MultiTurnRun(runId, scenarioId, agentConfig, MultiTurnRun.Status.PENDING, 0, 0, 0, null, Map.of(), null, null, Instant.now());
        runRepository.save(run);
        return run;
    }

    @Transactional
    public void startRun(@NonNull String runId) {
        Optional<MultiTurnRun> opt = runRepository.findById(runId);
        if (opt.isEmpty()) return;
        MultiTurnRun run = opt.get();
        if (run.status() != MultiTurnRun.Status.PENDING) return;

        Optional<MultiTurnScenario> scenarioOpt = scenarioRepository.findById(run.scenarioId());
        if (scenarioOpt.isEmpty()) {
            runRepository.save(new MultiTurnRun(runId, run.scenarioId(), run.agentConfig(), MultiTurnRun.Status.FAILED, 0, 0, 0, 0.0, Map.of("error", "Scenario not found"), null, Instant.now(), run.createdAt()));
            return;
        }

        runRepository.save(new MultiTurnRun(runId, run.scenarioId(), run.agentConfig(), MultiTurnRun.Status.RUNNING, 0, 0, 0, null, run.summaryMetrics(), Instant.now(), null, run.createdAt()));
        CompletableFuture.runAsync(() -> executeRun(runId, scenarioOpt.get(), run.agentConfig()), executor);
    }

    private void executeRun(@NonNull String runId, @NonNull MultiTurnScenario scenario, @NonNull Map<String, Object> agentConfig) {
        try {
            List<Map<String, String>> conversation = new ArrayList<>();
            int passed = 0;
            int failed = 0;

            for (int i = 0; i < scenario.turns().size(); i++) {
                MultiTurnScenario.Turn turn = scenario.turns().get(i);
                String turnId = runId + "-t" + i;

                long start = System.currentTimeMillis();

                // Build messages: conversation history + current turn
                List<Map<String, String>> messages = new ArrayList<>(conversation);
                messages.add(Map.of("role", turn.role(), "content", turn.message()));

                Map<String, Object> config = new HashMap<>(agentConfig);
                config.put("messages", messages);
                String configJson = mapper.writeValueAsString(config);

                String output = agentInvoker.invoke(configJson, turn.message());
                long latency = System.currentTimeMillis() - start;

                // Evaluate keywords
                List<String> matched = new ArrayList<>();
                String outputLower = output.toLowerCase();
                for (String kw : turn.expectedKeywords()) {
                    if (outputLower.contains(kw.toLowerCase())) {
                        matched.add(kw);
                    }
                }

                double score = turn.expectedKeywords().isEmpty() ? 1.0 :
                    (double) matched.size() / turn.expectedKeywords().size();
                boolean turnPassed = score >= 0.5;
                if (turnPassed) passed++; else failed++;

                MultiTurnTurn turnRecord = new MultiTurnTurn(
                    turnId, runId, i, turn.role(), turn.message(), output,
                    turn.expectedKeywords(), matched, score, turnPassed, latency,
                    Map.of(), Instant.now()
                );
                turnRepository.save(turnRecord);

                // Add assistant response to conversation history
                conversation.add(Map.of("role", "user", "content", turn.message()));
                conversation.add(Map.of("role", "assistant", "content", output));
            }

            int total = scenario.turns().size();
            double finalScore = total > 0 ? (double) passed / total : 0.0;

            Map<String, Object> summary = Map.of(
                "totalTurns", total,
                "passedTurns", passed,
                "failedTurns", failed,
                "finalScore", finalScore
            );

            runRepository.save(new MultiTurnRun(
                runId, scenario.scenarioId(), agentConfig, MultiTurnRun.Status.COMPLETED,
                total, passed, failed, finalScore, summary,
                runRepository.findById(runId).map(MultiTurnRun::startedAt).orElse(null),
                Instant.now(),
                runRepository.findById(runId).map(MultiTurnRun::createdAt).orElse(Instant.now())
            ));

            LOG.info("Multi-turn run {} completed: {}/{} turns passed", runId, passed, total);
        } catch (Exception e) {
            LOG.error("Multi-turn run {} failed", runId, e);
            Optional<MultiTurnRun> opt = runRepository.findById(runId);
            if (opt.isPresent()) {
                MultiTurnRun run = opt.get();
                runRepository.save(new MultiTurnRun(
                    runId, run.scenarioId(), run.agentConfig(), MultiTurnRun.Status.FAILED,
                    run.totalTurns(), run.passedTurns(), run.failedTurns(), run.finalScore(),
                    Map.of("error", e.getMessage()), run.startedAt(), Instant.now(), run.createdAt()
                ));
            }
        }
    }

    public @NonNull Optional<MultiTurnRun> getRun(@NonNull String runId) {
        return runRepository.findById(runId);
    }

    public @NonNull List<MultiTurnRun> listRuns() {
        return runRepository.findAll();
    }

    public @NonNull List<MultiTurnTurn> getTurns(@NonNull String runId) {
        return turnRepository.findByRunId(runId);
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
    }
}
