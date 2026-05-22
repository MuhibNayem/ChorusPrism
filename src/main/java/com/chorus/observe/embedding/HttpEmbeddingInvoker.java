package com.chorus.observe.embedding;

import com.chorus.observe.service.AgentInvocationException;
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
 * HTTP-based embedding invoker that calls standard embedding endpoints
 * (e.g., OpenAI {@code /v1/embeddings}).
 */
public final class HttpEmbeddingInvoker implements EmbeddingInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(HttpEmbeddingInvoker.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final String endpoint;

    public HttpEmbeddingInvoker(@NonNull ObjectMapper mapper, @NonNull String endpoint) {
        this(mapper, endpoint, Duration.ofSeconds(60));
    }

    public HttpEmbeddingInvoker(@NonNull ObjectMapper mapper, @NonNull String endpoint, @NonNull Duration timeout) {
        this.mapper = Objects.requireNonNull(mapper);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.timeout = Objects.requireNonNull(timeout);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public @NonNull float[] embed(@NonNull String model, @NonNull String text) {
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "input", text
            );
            String requestBody = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseEmbedding(response.body());
            }
            throw new AgentInvocationException(
                "Embedding endpoint returned " + response.statusCode(),
                response.statusCode(), response.body());
        } catch (AgentInvocationException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentInvocationException("Embedding request failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] parseEmbedding(@NonNull String body) throws Exception {
        Map<String, Object> root = mapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        Object data = root.get("data");
        if (data instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                Object embedding = map.get("embedding");
                if (embedding instanceof List<?> embList) {
                    float[] vector = new float[embList.size()];
                    for (int i = 0; i < embList.size(); i++) {
                        vector[i] = ((Number) embList.get(i)).floatValue();
                    }
                    return vector;
                }
            }
        }
        // Fallback: try root-level array
        try {
            List<Double> values = mapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }
            return vector;
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected embedding response format: " + body);
        }
    }
}
