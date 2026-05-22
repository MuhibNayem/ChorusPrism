package com.chorus.observe.api;

import com.chorus.observe.model.Feedback;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.service.FeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
            @RequestBody @Valid @NonNull FeedbackRequest request) {
        Feedback feedback = feedbackService.submitFeedback(
            runId, request.spanId(), request.score(), request.label(), request.comment(), request.source()
        );
        return ResponseEntity.ok(feedback);
    }

    @GetMapping("/feedback")
    public ResponseEntity<PagedResult<Feedback>> getFeedback(
            @PathVariable @NonNull String runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(feedbackService.getFeedbackForRun(runId, page, size));
    }

    public record FeedbackRequest(
        String spanId,
        Double score,
        String label,
        String comment,
        @NotNull String source
    ) {
        public FeedbackRequest {
            source = source != null ? source : "human";
        }
    }
}
