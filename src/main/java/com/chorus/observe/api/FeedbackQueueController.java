package com.chorus.observe.api;

import com.chorus.observe.model.PagedResult;
import com.chorus.observe.persistence.FeedbackRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API v1 for the annotation queue (feedback review).
 */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackQueueController {

    private final FeedbackRepository feedbackRepository;

    public FeedbackQueueController(@NonNull FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @GetMapping
    public ResponseEntity<?> listFeedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of(
            "items", List.of(),
            "total", 0,
            "page", page,
            "size", size
        ));
    }

    @GetMapping("/queue")
    public ResponseEntity<?> listQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Well-structured mock data for the annotation queue
        List<Map<String, Object>> items = List.of(
            Map.of(
                "queueId", "q_001", "runId", "run-router-8921", "agentId", "intent-router",
                "model", "gpt-4", "input", "I want to change my subscription plan",
                "output", "Routing to billing agent...", "status", "pending"
            ),
            Map.of(
                "queueId", "q_002", "runId", "run-support-4412", "agentId", "support-bot",
                "model", "claude-3-sonnet", "input", "My API key stopped working overnight",
                "output", "Please check your API key expiration date and regenerate if needed.",
                "status", "pending"
            ),
            Map.of(
                "queueId", "q_003", "runId", "run-sales-1029", "agentId", "sales-assistant",
                "model", "gpt-4o", "input", "Do you offer enterprise pricing?",
                "output", "Yes, we offer custom enterprise plans. Please contact sales@example.com.",
                "status", "annotated", "score", 4,
                "comment", "Good response but could include pricing tiers",
                "annotatedAt", Instant.now().minusSeconds(86400).toString()
            )
        );
        return ResponseEntity.ok(Map.of(
            "items", items,
            "total", items.size(),
            "page", page,
            "size", size
        ));
    }

    @PostMapping("/queue/{queueId}")
    public ResponseEntity<?> submitAnnotation(@PathVariable String queueId, @RequestBody Map<String, Object> request) {
        Object scoreObj = request.get("score");
        Integer score = scoreObj instanceof Number ? ((Number) scoreObj).intValue() : null;
        String comment = (String) request.get("comment");
        Boolean skip = (Boolean) request.get("skip");

        String status = Boolean.TRUE.equals(skip) ? "skipped" : "annotated";
        return ResponseEntity.ok(Map.of(
            "queueId", queueId,
            "status", status,
            "score", score,
            "comment", comment,
            "annotatedAt", Instant.now().toString()
        ));
    }
}
