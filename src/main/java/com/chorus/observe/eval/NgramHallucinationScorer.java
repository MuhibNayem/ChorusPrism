package com.chorus.observe.eval;

import com.chorus.observe.model.LlmCall;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Hallucination scorer based on n-gram overlap between source text (prompt + messages)
 * and completion text. Lower overlap implies higher hallucination risk.
 */
public class NgramHallucinationScorer implements HallucinationScorer {

    @Override
    public double score(@NonNull List<LlmCall> calls, @NonNull Map<String, Object> config) {
        if (calls.isEmpty()) return 0.0;

        int n = config.get("ngramSize") instanceof Number num ? num.intValue() : 2;
        if (n < 1) n = 2;

        StringBuilder sourceBuilder = new StringBuilder();
        StringBuilder completionBuilder = new StringBuilder();

        for (LlmCall call : calls) {
            if (call.prompt() != null) sourceBuilder.append(call.prompt()).append(' ');
            if (call.messages() != null) {
                for (LlmCall.LlmMessage msg : call.messages()) {
                    sourceBuilder.append(msg.text()).append(' ');
                }
            }
            if (call.completion() != null) completionBuilder.append(call.completion()).append(' ');
        }

        String source = normalize(sourceBuilder.toString());
        String completion = normalize(completionBuilder.toString());

        if (source.isEmpty() || completion.isEmpty()) return 0.0;

        Set<String> sourceNgrams = ngrams(source, n);
        Set<String> completionNgrams = ngrams(completion, n);

        if (completionNgrams.isEmpty()) return 0.0;

        long overlap = completionNgrams.stream().filter(sourceNgrams::contains).count();
        double overlapRatio = (double) overlap / completionNgrams.size();

        return Math.min(1.0, Math.max(0.0, 1.0 - overlapRatio));
    }

    private String normalize(String text) {
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private Set<String> ngrams(String text, int n) {
        String[] tokens = text.split(" ");
        if (tokens.length < n) return Set.of();
        Set<String> result = new HashSet<>();
        for (int i = 0; i <= tokens.length - n; i++) {
            result.add(String.join(" ", Arrays.copyOfRange(tokens, i, i + n)));
        }
        return result;
    }
}
