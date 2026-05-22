package com.chorus.observe.persistence;

import com.chorus.observe.model.Span;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InMemorySpanRepository extends SpanRepository {
    private final List<Span> store = new ArrayList<>();

    public InMemorySpanRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(Span span) {
        store.removeIf(s -> s.spanId().equals(span.spanId()));
        store.add(span);
    }

    @Override
    public List<Span> findByRunId(String runId) {
        return store.stream()
            .filter(s -> s.runId().equals(runId))
            .sorted((a, b) -> a.startTime().compareTo(b.startTime()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Span> findByRunId(String runId, int limit, int offset) {
        return store.stream()
            .filter(s -> s.runId().equals(runId))
            .sorted((a, b) -> a.startTime().compareTo(b.startTime()))
            .skip(offset).limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public long countByRunId(String runId) {
        return store.stream().filter(s -> s.runId().equals(runId)).count();
    }

    @Override
    public List<Span> findByRunIdAndKind(String runId, Span.Kind kind) {
        return store.stream()
            .filter(s -> s.runId().equals(runId) && s.kind() == kind)
            .collect(Collectors.toList());
    }
}
