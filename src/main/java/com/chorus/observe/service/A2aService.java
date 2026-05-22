package com.chorus.observe.service;

import com.chorus.observe.model.Run;
import com.chorus.observe.persistence.RunRepository;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for A2A (Agent-to-Agent) protocol support.
 */
public class A2aService {

    private final RunRepository runRepository;

    public A2aService(@NonNull RunRepository runRepository) {
        this.runRepository = Objects.requireNonNull(runRepository);
    }

    /**
     * Returns the A2A Agent Card for Chorus Observe.
     */
    public @NonNull Map<String, Object> getAgentCard() {
        return Map.of(
            "name", "Chorus Observe",
            "description", "Observability and evaluation backend for LLM agents",
            "version", "1.0.0",
            "url", "http://localhost:8080",
            "capabilities", Map.of(
                "traceIngestion", true,
                "evalEngine", true,
                "redTeaming", true,
                "promptManagement", true,
                "sqlQuery", true
            ),
            "skills", List.of(
                Map.of("id", "get_recent_runs", "name", "Get Recent Runs", "description", "Retrieve recent agent execution runs"),
                Map.of("id", "get_run_detail", "name", "Get Run Detail", "description", "Retrieve full detail for a specific run"),
                Map.of("id", "submit_eval", "name", "Submit Evaluation", "description", "Run an evaluation dataset against an agent"),
                Map.of("id", "query_traces", "name", "Query Traces", "description", "Execute SQL queries against trace data")
            )
        );
    }
}
