package com.chorus.observe.service;

import com.chorus.observe.model.Feedback;
import com.chorus.observe.persistence.FeedbackRepository;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for feedback operations.
 */
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(@NonNull FeedbackRepository feedbackRepository) {
        this.feedbackRepository = Objects.requireNonNull(feedbackRepository);
    }

    public @NonNull Feedback submitFeedback(
            @NonNull String runId,
            String spanId,
            Double score,
            String label,
            String comment,
            @NonNull String source) {
        Feedback feedback = new Feedback(
            UUID.randomUUID().toString(),
            runId,
            spanId,
            score,
            label,
            comment,
            source,
            Instant.now()
        );
        feedbackRepository.save(feedback);
        return feedback;
    }

    public @NonNull List<Feedback> getFeedbackForRun(@NonNull String runId) {
        return feedbackRepository.findByRunId(runId);
    }

    public @NonNull Optional<Feedback> getFeedback(@NonNull String feedbackId) {
        return feedbackRepository.findById(feedbackId);
    }
}
