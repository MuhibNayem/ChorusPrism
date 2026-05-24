package com.chorus.observe.eval;

import com.chorus.observe.model.LlmCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional hallucination scorer that delegates to an external LLM-as-a-judge endpoint.
 * Falls back to 0.0 if the endpoint is unreachable or returns an invalid response.
 */
public class LlmJudgeHallucinationScorer implements HallucinationScorer {

    private static final Logger LOG = LoggerFactory.getLogger(LlmJudgeHallucinationScorer.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public LlmJudgeHallucinationScorer(@NonNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public double score(@NonNull List<LlmCall> calls, @NonNull Map<String, Object> config) {
        Object urlObj = config.get("llmJudgeUrl");
        if (urlObj == null) {
            LOG.debug("llmJudgeUrl not configured; skipping LLM-judge scorer");
            return 0.0;
        }

        StringBuilder promptBuilder = new StringBuilder();
        StringBuilder completionBuilder = new StringBuilder();
        for (LlmCall call : calls) {
            if (call.prompt() != null) promptBuilder.append(call.prompt()).append("\n");
            if (call.messages() != null) {
                for (LlmCall.LlmMessage msg : call.messages()) {
                    promptBuilder.append(msg.role()).append(": ").append(msg.text()).append("\n");
                }
            }
            if (call.completion() != null) completionBuilder.append(call.completion()).append("\n");
        }

        String prompt = promptBuilder.toString().trim();
        String completion = completionBuilder.toString().trim();

        if (prompt.isEmpty() || completion.isEmpty()) return 0.0;

        try {
            Map<String, Object> payload = Map.of(
                "prompt", prompt,
                "completion", completion,
                "task", "hallucination"
            );
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlObj.toString()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<?, ?> result = mapper.readValue(response.body(), Map.class);
                Object scoreObj = result.get("score");
                if (scoreObj instanceof Number n) {
                    double score = n.doubleValue();
                    return Math.min(1.0, Math.max(0.0, score));
                }
                LOG.warn("LLM judge response missing 'score' field");
            } else {
                LOG.warn("LLM judge returned HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            LOG.error("LLM judge request failed", e);
        }
        return 0.0;
    }
}
