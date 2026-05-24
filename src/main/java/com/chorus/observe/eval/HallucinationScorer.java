package com.chorus.observe.eval;

import com.chorus.observe.model.LlmCall;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for scoring hallucination risk in LLM completions.
 * Score range: 0.0 (no hallucination detected) to 1.0 (high hallucination risk).
 */
public interface HallucinationScorer {
    /**
     * Computes a hallucination score for the given LLM calls.
     *
     * @param calls  the LLM calls within a run
     * @param config evaluator-specific configuration (e.g., ngramSize, llmJudgeUrl)
     * @return score between 0.0 and 1.0
     */
    double score(@NonNull List<LlmCall> calls, @NonNull Map<String, Object> config);
}
