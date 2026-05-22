package com.chorus.observe.service;

import com.chorus.engine.evals.EvalCase;
import com.chorus.engine.evals.EvalResult;
import com.chorus.engine.evals.EvalScorer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-judge scorer that uses the {@link AgentInvoker} abstraction.
 * <p>
 * Sends a structured judging prompt to the configured agent endpoint and extracts
 * a 0-10 numeric rating from the response. Does not require a direct {@code LlmClient}
 * dependency, making it suitable for the observe server which only has {@code AgentInvoker}.
 */
public final class AgentInvokerJudgeScorer implements EvalScorer {

    private static final Logger LOG = LoggerFactory.getLogger(AgentInvokerJudgeScorer.class);
    private static final Pattern RATING_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final AgentInvoker agentInvoker;
    private final double passThreshold;
    private final String judgePromptTemplate;

    public AgentInvokerJudgeScorer(@NonNull AgentInvoker agentInvoker, double passThreshold) {
        this(agentInvoker, passThreshold, null);
    }

    public AgentInvokerJudgeScorer(
            @NonNull AgentInvoker agentInvoker,
            double passThreshold,
            @Nullable String judgePromptTemplate
    ) {
        this.agentInvoker = Objects.requireNonNull(agentInvoker);
        if (passThreshold < 0 || passThreshold > 1) {
            throw new IllegalArgumentException("passThreshold must be in [0, 1]");
        }
        this.passThreshold = passThreshold;
        this.judgePromptTemplate = judgePromptTemplate != null ? judgePromptTemplate : DEFAULT_TEMPLATE;
    }

    @Override
    public @NonNull EvalResult score(@NonNull EvalCase testCase, @NonNull String actualOutput) {
        String prompt = buildPrompt(testCase.input(), testCase.expectedOutput(), actualOutput);
        try {
            String response = agentInvoker.invoke("{}", prompt);
            double rating = extractRating(response);
            double score = rating / 10.0;
            boolean passed = score >= passThreshold;

            String reasoning = String.format(
                "LLM judge rated %.1f/10 (threshold: %.1f/10). Raw response: %s",
                rating, passThreshold * 10, response.replace("\n", " "));

            return new EvalResult(testCase.id(), passed, score, actualOutput, reasoning);
        } catch (Exception e) {
            LOG.warn("LLM judge scoring failed for case {}", testCase.id(), e);
            return new EvalResult(testCase.id(), false, 0.0, actualOutput,
                "LLM judge failed: " + e.getMessage());
        }
    }

    private @NonNull String buildPrompt(@NonNull String question, @Nullable String expected, @NonNull String actual) {
        return judgePromptTemplate
            .replace("{question}", question)
            .replace("{expected}", expected != null ? expected : "")
            .replace("{actual}", actual);
    }

    private double extractRating(@NonNull String content) {
        Matcher matcher = RATING_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                double rating = Double.parseDouble(matcher.group(1));
                return Math.max(0, Math.min(10, rating));
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return 0.0;
    }

    private static final String DEFAULT_TEMPLATE = """
        You are an expert evaluator. Rate how well the ACTUAL OUTPUT answers the QUESTION compared to the EXPECTED OUTPUT.

        QUESTION: {question}

        EXPECTED OUTPUT: {expected}

        ACTUAL OUTPUT: {actual}

        Provide a rating from 0 to 10, where 10 means the actual output is as good as or better than the expected output.
        Start your response with the numeric rating, followed by a brief explanation.
        """;
}
