package com.chorus.observe.persistence;

import com.chorus.observe.model.Feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class InMemoryFeedbackRepository extends FeedbackRepository {
    private final List<Feedback> store = new ArrayList<>();

    public InMemoryFeedbackRepository() {
        super(null);
    }

    @Override
    public void save(Feedback feedback) {
        store.removeIf(f -> f.feedbackId().equals(feedback.feedbackId()));
        store.add(feedback);
    }

    @Override
    public List<Feedback> findByRunId(String runId) {
        return store.stream()
            .filter(f -> f.runId().equals(runId))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Feedback> findByRunId(String runId, int limit, int offset) {
        return store.stream()
            .filter(f -> f.runId().equals(runId))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .skip(offset).limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public long countByRunId(String runId) {
        return store.stream().filter(f -> f.runId().equals(runId)).count();
    }

    @Override
    public Optional<Feedback> findById(String feedbackId) {
        return store.stream()
            .filter(f -> f.feedbackId().equals(feedbackId))
            .findFirst();
    }
}
