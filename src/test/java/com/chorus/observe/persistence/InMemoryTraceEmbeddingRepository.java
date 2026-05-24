package com.chorus.observe.persistence;

import com.chorus.observe.model.TraceEmbedding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class InMemoryTraceEmbeddingRepository extends TraceEmbeddingRepository {
    private final List<TraceEmbedding> store = new ArrayList<>();

    public InMemoryTraceEmbeddingRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(@NonNull TraceEmbedding embedding) {
        store.removeIf(e -> e.embeddingId().equals(embedding.embeddingId()));
        store.add(embedding);
    }

    @Override
    public @NonNull Optional<TraceEmbedding> findById(@NonNull String embeddingId) {
        return store.stream()
            .filter(e -> e.embeddingId().equals(embeddingId))
            .findFirst();
    }

    @Override
    public @NonNull List<TraceEmbedding> findByRunId(@NonNull String runId) {
        return store.stream()
            .filter(e -> e.runId().equals(runId))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .collect(Collectors.toList());
    }

    @Override
    public @NonNull List<TraceEmbedding> findByModel(@NonNull String model) {
        return store.stream()
            .filter(e -> e.model().equals(model))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .collect(Collectors.toList());
    }

    @Override
    public @NonNull List<TraceEmbedding> findByModel(@NonNull String model, int limit) {
        return store.stream()
            .filter(e -> e.model().equals(model))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public @NonNull List<TraceEmbedding> findAll() {
        return store.stream()
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .collect(Collectors.toList());
    }

    @Override
    public @NonNull List<TraceEmbedding> findNearestNeighbors(@NonNull float[] queryVector, int limit) {
        return findAll().stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public void deleteByRunId(@NonNull String runId) {
        store.removeIf(e -> e.runId().equals(runId));
    }
}
