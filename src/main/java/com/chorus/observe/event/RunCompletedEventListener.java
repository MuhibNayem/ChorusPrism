package com.chorus.observe.event;

import com.chorus.observe.model.Run;
import com.chorus.observe.model.Evaluator;
import com.chorus.observe.model.RunEvaluation;
import com.chorus.observe.model.EvalLoop;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.persistence.EvalLoopRepository;
import com.chorus.observe.persistence.EvaluatorRepository;
import com.chorus.observe.service.EvaluatorService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Listens for run completion events and automatically triggers configured evaluators.
 */
@Component
public class RunCompletedEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(RunCompletedEventListener.class);
    private static final Random RANDOM = new Random();

    private final EvaluatorRepository evaluatorRepository;
    private final EvaluatorService evaluatorService;
    private final RunRepository runRepository;
    private final EvalLoopRepository evalLoopRepository;

    public RunCompletedEventListener(@NonNull EvaluatorRepository evaluatorRepository,
                                     @NonNull EvaluatorService evaluatorService,
                                     @NonNull RunRepository runRepository,
                                     @NonNull EvalLoopRepository evalLoopRepository) {
        this.evaluatorRepository = Objects.requireNonNull(evaluatorRepository);
        this.evaluatorService = Objects.requireNonNull(evaluatorService);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.evalLoopRepository = Objects.requireNonNull(evalLoopRepository);
    }

    @EventListener
    public void onRunCompleted(@NonNull RunCompletedEvent event) {
        LOG.debug("Run completed: {} (status={})", event.runId(), event.status());

        // 1. Legacy Behavior: Trigger all evaluators of kind "hallucination"
        List<Evaluator> legacyEvaluators = evaluatorRepository.findByKind("hallucination");
        for (Evaluator evaluator : legacyEvaluators) {
            try {
                evaluatorService.evaluateRun(event.runId(), evaluator.evaluatorId());
                LOG.debug("Triggered legacy hallucination evaluator {} for run {}", evaluator.evaluatorId(), event.runId());
            } catch (Exception e) {
                LOG.error("Failed to trigger legacy evaluator {} for run {}", evaluator.evaluatorId(), event.runId(), e);
            }
        }

        // 2. Persistent Loops Pipeline: Trigger scheduled continuous loops based on agentId
        Optional<Run> runOpt = runRepository.findById(event.runId());
        if (runOpt.isEmpty()) {
            LOG.debug("Run completed event target run {} not found in repository", event.runId());
            return;
        }
        Run run = runOpt.get();

        List<EvalLoop> loops = evalLoopRepository.findByAgentId(run.agentId());
        for (EvalLoop loop : loops) {
            if (!"ACTIVE".equalsIgnoreCase(loop.status())) {
                continue;
            }

            // Sampling rate check
            if (loop.samplingRate() < 100) {
                if (RANDOM.nextInt(100) >= loop.samplingRate()) {
                    LOG.debug("Continuous evaluation loop {} skipped run {} due to sampling configuration", loop.loopId(), run.runId());
                    continue;
                }
            }

            try {
                RunEvaluation result = evaluatorService.evaluateRun(run.runId(), loop.evaluatorId());
                LOG.debug("Continuous evaluation loop {} evaluated run {} (score={})", loop.loopId(), run.runId(), result.score());

                // Update loop last_run_at status timestamp
                evalLoopRepository.save(new EvalLoop(
                    loop.loopId(), loop.agentId(), loop.evaluatorId(), loop.samplingRate(),
                    loop.alertThreshold(), loop.status(), loop.createdAt(), Instant.now()
                ));

                // Threshold breaches trigger notification warnings
                if (result.score() < loop.alertThreshold()) {
                    LOG.warn("Continuous evaluation alert breach on loop {} (agent {}): score {} < threshold {}",
                        loop.loopId(), run.agentId(), result.score(), loop.alertThreshold());
                }
            } catch (Exception e) {
                LOG.error("Continuous evaluation loop {} execution failed for run {}", loop.loopId(), run.runId(), e);
            }
        }
    }
}
