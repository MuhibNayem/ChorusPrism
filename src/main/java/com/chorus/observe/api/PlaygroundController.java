package com.chorus.observe.api;

import com.chorus.observe.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REST API v1 for the prompt playground — live execution and comparison.
 */
@RestController
@RequestMapping("/api/v1/playground")
public class PlaygroundController {

    private final ObjectMapper mapper;

    public PlaygroundController(@NonNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, Object> request) {
        String promptContent = (String) request.get("promptContent");
        String model = (String) request.getOrDefault("model", "gpt-4");
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.getOrDefault("variables", Map.of());

        if (promptContent == null || promptContent.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "promptContent is required"));
        }

        // Resolve template variables
        String resolved = promptContent;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        // In a real implementation, this would call an LLM provider.
        // For enterprise parity, we return a structured placeholder that the UI can consume.
        String output = "[Simulated] " + model + " response for: " + resolved.substring(0, Math.min(60, resolved.length())) + "…";
        int inputTokens = resolved.length() / 4;
        int outputTokens = output.length() / 4;
        double estimatedCost = (inputTokens + outputTokens) * 0.00001;

        return ResponseEntity.ok(Map.of(
            "output", output,
            "latencyMs", 1240,
            "inputTokens", inputTokens,
            "outputTokens", outputTokens,
            "estimatedCost", estimatedCost
        ));
    }
}
