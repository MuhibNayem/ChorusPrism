package com.chorus.observe.api;

import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API v1 for RAG (Retrieval-Augmented Generation) metrics.
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics(@RequestParam(defaultValue = "24h") String window) {
        Map<String, Object> metrics = Map.of(
            "queryCount", 45231,
            "avgContextPrecision", 0.87,
            "avgContextRecall", 0.79,
            "avgLatencyMs", 245,
            "hitRate", 0.94,
            "latencyDistribution", List.of(
                Map.of("bucket", "<50ms", "count", 4523),
                Map.of("bucket", "50-100ms", "count", 12450),
                Map.of("bucket", "100-200ms", "count", 18920),
                Map.of("bucket", "200-500ms", "count", 7230),
                Map.of("bucket", ">500ms", "count", 2108)
            ),
            "topQueries", List.of(
                Map.of("query", "billing cycle explanation", "count", 1240, "avgScore", 0.92),
                Map.of("query", "api rate limits", "count", 980, "avgScore", 0.88),
                Map.of("query", "enterprise pricing", "count", 850, "avgScore", 0.85),
                Map.of("query", "integration guide", "count", 720, "avgScore", 0.91),
                Map.of("query", "troubleshooting 500 errors", "count", 640, "avgScore", 0.87)
            )
        );
        return ResponseEntity.ok(metrics);
    }
}
