package com.chorus.observe.event;

import com.chorus.observe.model.*;
import com.chorus.observe.persistence.InMemoryEvaluatorRepository;
import com.chorus.observe.persistence.InMemoryLlmCallRepository;
import com.chorus.observe.persistence.InMemoryRunEvaluationRepository;
import com.chorus.observe.persistence.InMemoryRunRepository;
import com.chorus.observe.persistence.InMemoryEvalLoopRepository;
import com.chorus.observe.eval.NgramHallucinationScorer;
import com.chorus.observe.service.EvaluatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RunCompletedEventListenerTest {

    private InMemoryEvaluatorRepository evaluatorRepository;
    private InMemoryRunEvaluationRepository runEvaluationRepository;
    private InMemoryLlmCallRepository llmCallRepository;
    private InMemoryRunRepository runRepository;
    private InMemoryEvalLoopRepository evalLoopRepository;
    private EvaluatorService evaluatorService;
    private RunCompletedEventListener listener;
    private AtomicInteger evaluateCallCount;

    @BeforeEach
    void setUp() {
        evaluatorRepository = new InMemoryEvaluatorRepository();
        runEvaluationRepository = new InMemoryRunEvaluationRepository();
        llmCallRepository = new InMemoryLlmCallRepository();
        runRepository = new InMemoryRunRepository();
        evalLoopRepository = new InMemoryEvalLoopRepository();
        evaluateCallCount = new AtomicInteger(0);

        evaluatorService = new EvaluatorService(
            evaluatorRepository, runEvaluationRepository, llmCallRepository,
            runRepository, (config, input) -> "10/10 perfect",
            new NgramHallucinationScorer(), null
        ) {
            @Override
            public RunEvaluation evaluateRun(String runId, String evaluatorId) {
                evaluateCallCount.incrementAndGet();
                return super.evaluateRun(runId, evaluatorId);
            }
        };

        listener = new RunCompletedEventListener(evaluatorRepository, evaluatorService, runRepository, evalLoopRepository);
    }

    @Test
    void shouldTriggerHallucinationEvaluatorsOnRunComplete() {
        Evaluator evaluator = new Evaluator("ev-1", "Hallucination Check", "hallucination", null, Map.of("threshold", 0.5));
        evaluatorRepository.save(evaluator);

        LlmCall call = new LlmCall("c1", "s1", "run-1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "hello world", "hello world", List.of(), null);
        llmCallRepository.save(call);

        RunCompletedEvent event = new RunCompletedEvent(this, "run-1", "tenant-1", Run.Status.SUCCESS);
        listener.onRunCompleted(event);

        assertThat(evaluateCallCount.get()).isEqualTo(1);
        List<RunEvaluation> evaluations = runEvaluationRepository.findByRunId("run-1");
        assertThat(evaluations).hasSize(1);
    }

    @Test
    void shouldNotTriggerNonHallucinationEvaluators() {
        Evaluator evaluator = new Evaluator("ev-1", "Regex Check", "regex", null, Map.of());
        evaluatorRepository.save(evaluator);

        RunCompletedEvent event = new RunCompletedEvent(this, "run-1", "tenant-1", Run.Status.SUCCESS);
        listener.onRunCompleted(event);

        assertThat(evaluateCallCount.get()).isEqualTo(0);
    }

    @Test
    void shouldTriggerMultipleEvaluators() {
        evaluatorRepository.save(new Evaluator("ev-1", "Check 1", "hallucination", null, Map.of()));
        evaluatorRepository.save(new Evaluator("ev-2", "Check 2", "hallucination", null, Map.of()));
        evaluatorRepository.save(new Evaluator("ev-3", "Check 3", "regex", null, Map.of()));

        RunCompletedEvent event = new RunCompletedEvent(this, "run-1", "tenant-1", Run.Status.ERROR);
        listener.onRunCompleted(event);

        assertThat(evaluateCallCount.get()).isEqualTo(2);
    }

    @Test
    void shouldNotFailWhenNoEvaluatorsExist() {
        RunCompletedEvent event = new RunCompletedEvent(this, "run-1", "tenant-1", Run.Status.SUCCESS);
        listener.onRunCompleted(event);

        assertThat(evaluateCallCount.get()).isEqualTo(0);
    }

    @Test
    void shouldContinueTriggeringAfterOneEvaluatorFails() {
        evaluatorRepository.save(new Evaluator("ev-1", "Check 1", "hallucination", null, Map.of()));
        evaluatorRepository.save(new Evaluator("ev-2", "Check 2", "hallucination", null, Map.of()));

        EvaluatorService failingService = new EvaluatorService(
            evaluatorRepository, runEvaluationRepository, llmCallRepository,
            runRepository, (config, input) -> "10/10 perfect",
            new NgramHallucinationScorer(), null
        ) {
            @Override
            public RunEvaluation evaluateRun(String runId, String evaluatorId) {
                if ("ev-1".equals(evaluatorId)) {
                    throw new RuntimeException("Simulated failure");
                }
                evaluateCallCount.incrementAndGet();
                return super.evaluateRun(runId, evaluatorId);
            }
        };

        RunCompletedEventListener failingListener = new RunCompletedEventListener(evaluatorRepository, failingService, runRepository, evalLoopRepository);
        RunCompletedEvent event = new RunCompletedEvent(this, "run-1", "tenant-1", Run.Status.SUCCESS);
        failingListener.onRunCompleted(event);

        // ev-1 failed but ev-2 should still be triggered
        assertThat(evaluateCallCount.get()).isEqualTo(1);
    }
}
