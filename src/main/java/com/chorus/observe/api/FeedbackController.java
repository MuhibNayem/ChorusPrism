package com.chorus.observe.api;

import com.chorus.observe.model.Feedback;
import com.chorus.observe.service.FeedbackService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * REST API v1 for feedback.
 */
@RestController
@RequestMapping("/api/v1/runs/{runId}")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(@NonNull FeedbackService feedbackService) {
        this.feedbackService = Objects.requireNonNull(feedbackService);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Feedback> submitFeedback(
            @PathVariable @NonNull String runId,
            @RequestBody @NonNull FeedbackRequest request) {
        Feedback feedback = feedbackService.submitFeedback(
            runId, request.spanId(), request.score(), request.label(), request.comment(), request.source()
        );
        return ResponseEntity.ok(feedback);
    }

    @GetMapping("/feedback")
    public ResponseEntity<List<Feedback>> getFeedback(@PathVariable @NonNull String runId) {
        return ResponseEntity.ok(feedbackService.getFeedbackForRun(runId));
    }

    public record FeedbackRequest(
        String spanId,
        Double score,
        String label,
        String comment,
        String source
    ) {
        public FeedbackRequest {
            source = source != null ? source : "human";
        }
    }
}
