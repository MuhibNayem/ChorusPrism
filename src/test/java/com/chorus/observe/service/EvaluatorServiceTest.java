package com.chorus.observe.service;

import com.chorus.observe.eval.NgramHallucinationScorer;
import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluatorServiceTest {

    private InMemoryEvaluatorRepository evaluatorRepository;
    private InMemoryRunEvaluationRepository runEvaluationRepository;
    private InMemoryLlmCallRepository llmCallRepository;
    private EvaluatorService evaluatorService;

    @BeforeEach
    void setUp() {
        evaluatorRepository = new InMemoryEvaluatorRepository();
        runEvaluationRepository = new InMemoryRunEvaluationRepository();
        llmCallRepository = new InMemoryLlmCallRepository();
        evaluatorService = new EvaluatorService(
            evaluatorRepository, runEvaluationRepository, llmCallRepository,
            new NgramHallucinationScorer(), null
        );
    }

    @Test
    void shouldCreateEvaluator() {
        Evaluator evaluator = evaluatorService.createEvaluator("Hallucination Check", "hallucination", "Detects hallucinations", Map.of("threshold", 0.5));
        assertThat(evaluator.evaluatorId()).isNotBlank();
        assertThat(evaluator.kind()).isEqualTo("hallucination");
    }

    @Test
    void shouldEvaluateRunWithHallucinationScorer() {
        // Given
        Evaluator evaluator = evaluatorService.createEvaluator("Hallucination Check", "hallucination", null,
            Map.of("threshold", 0.5, "ngramSize", 2));

        LlmCall call = new LlmCall("c1", "s1", "run-1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "the quick brown fox", "the quick lazy dog", List.of(), null);
        llmCallRepository.save(call);

        // When
        RunEvaluation result = evaluatorService.evaluateRun("run-1", evaluator.evaluatorId());

        // Then
        assertThat(result.evaluationId()).isNotBlank();
        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.evaluatorId()).isEqualTo(evaluator.evaluatorId());
        assertThat(result.score()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(result.passed()).isTrue(); // score > 0.5 since only 1 of 3 bigrams overlap
        assertThat(result.details()).containsKey("scorer");
        assertThat(result.details().get("scorer")).isEqualTo("ngram");
    }

    @Test
    void shouldFailWhenScoreBelowThreshold() {
        Evaluator evaluator = evaluatorService.createEvaluator("Strict Check", "hallucination", null,
            Map.of("threshold", 0.95));

        // Identical prompt and completion → score = 0.0
        LlmCall call = new LlmCall("c1", "s1", "run-1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "hello world foo bar", "hello world foo bar", List.of(), null);
        llmCallRepository.save(call);

        RunEvaluation result = evaluatorService.evaluateRun("run-1", evaluator.evaluatorId());

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.passed()).isFalse();
    }

    @Test
    void shouldThrowForMissingEvaluator() {
        assertThatThrownBy(() -> evaluatorService.evaluateRun("run-1", "nonexistent"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Evaluator not found");
    }

    @Test
    void shouldReturnPendingForUnknownKind() {
        Evaluator evaluator = evaluatorService.createEvaluator("Unknown", "regex", null, Map.of());
        RunEvaluation result = evaluatorService.evaluateRun("run-1", evaluator.evaluatorId());

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.passed()).isFalse();
        assertThat(result.details().get("reason")).isEqualTo("kind not implemented: regex");
    }

    @Test
    void shouldUseDefaultThresholdWhenNotConfigured() {
        Evaluator evaluator = evaluatorService.createEvaluator("Default Threshold", "hallucination", null, Map.of());

        // Completely different text → score = 1.0
        LlmCall call = new LlmCall("c1", "s1", "run-1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "abc def ghi", "xyz jkl mno", List.of(), null);
        llmCallRepository.save(call);

        RunEvaluation result = evaluatorService.evaluateRun("run-1", evaluator.evaluatorId());

        // Default threshold is 0.7, score is 1.0 → passed
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.passed()).isTrue();
        assertThat(result.details().get("threshold")).isEqualTo(0.7);
    }

    @Test
    void shouldListRunEvaluations() {
        Evaluator evaluator = evaluatorService.createEvaluator("Hallucination Check", "hallucination", null, Map.of());
        LlmCall call = new LlmCall("c1", "s1", "run-1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "hello", "world", List.of(), null);
        llmCallRepository.save(call);

        evaluatorService.evaluateRun("run-1", evaluator.evaluatorId());

        List<RunEvaluation> evaluations = evaluatorService.getRunEvaluations("run-1");
        assertThat(evaluations).hasSize(1);
    }
}
