package com.chorus.observe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link AgentInvoker} that POSTs input to a configurable HTTP endpoint.
 * <p>
 * Throws {@link AgentInvocationException} on HTTP errors or network failures instead of
 * returning error strings, so callers can distinguish failures from valid outputs.
 */
public class HttpAgentInvoker implements AgentInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(HttpAgentInvoker.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration timeout;

    public HttpAgentInvoker(@NonNull ObjectMapper mapper) {
        this(mapper, Duration.ofMinutes(5));
    }

    public HttpAgentInvoker(@NonNull ObjectMapper mapper, @NonNull Duration timeout) {
        this.mapper = Objects.requireNonNull(mapper);
        this.timeout = Objects.requireNonNull(timeout);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public @NonNull String invoke(@NonNull String agentConfig, @NonNull String input) {
        try {
            Map<String, Object> config = mapper.readValue(agentConfig, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            String endpoint = config.getOrDefault("endpoint", "http://localhost:8080/invoke").toString();

            Map<String, Object> body = Map.of("input", input, "config", config);
            String requestBody = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new AgentInvocationException(
                "Agent endpoint returned HTTP " + response.statusCode(),
                response.statusCode(), response.body());
        } catch (AgentInvocationException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentInvocationException("Agent invocation failed", e);
        }
    }
}
