package com.chorus.observe.eval;

import com.chorus.observe.model.LlmCall;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NgramHallucinationScorerTest {

    private final NgramHallucinationScorer scorer = new NgramHallucinationScorer();

    @Test
    void emptyCallsShouldScoreZero() {
        double score = scorer.score(List.of(), Map.of());
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void identicalPromptAndCompletionShouldScoreZero() {
        LlmCall call = new LlmCall("c1", "s1", "r1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "hello world", "hello world", List.of(), null);
        double score = scorer.score(List.of(call), Map.of("ngramSize", 2));
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void completelyDifferentTextShouldScoreOne() {
        LlmCall call = new LlmCall("c1", "s1", "r1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "abc def ghi", "xyz jkl mno", List.of(), null);
        double score = scorer.score(List.of(call), Map.of("ngramSize", 2));
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void partialOverlapShouldGiveIntermediateScore() {
        LlmCall call = new LlmCall("c1", "s1", "r1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "the quick brown fox", "the quick lazy dog", List.of(), null);
        double score = scorer.score(List.of(call), Map.of("ngramSize", 2));
        // "the quick" overlaps (1 of 3 bigrams in completion: "the quick", "quick lazy", "lazy dog")
        assertThat(score).isGreaterThan(0.5).isLessThan(1.0);
    }

    @Test
    void messagesShouldBeIncludedInSource() {
        LlmCall call = new LlmCall("c1", "s1", "r1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            null, "hello world", List.of(),
            List.of(new LlmCall.LlmMessage("user", "hello world")));
        double score = scorer.score(List.of(call), Map.of("ngramSize", 2));
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void missingCompletionShouldScoreZero() {
        LlmCall call = new LlmCall("c1", "s1", "r1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "hello world", null, List.of(), null);
        double score = scorer.score(List.of(call), Map.of());
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void defaultNgramSizeIsTwo() {
        LlmCall call = new LlmCall("c1", "s1", "r1", "openai", "gpt-4",
            10, 5, BigDecimal.ZERO, 100L,
            "a b c d", "a b x y", List.of(), null);
        double scoreDefault = scorer.score(List.of(call), Map.of());
        double scoreExplicit = scorer.score(List.of(call), Map.of("ngramSize", 2));
        assertThat(scoreDefault).isEqualTo(scoreExplicit);
    }
}
