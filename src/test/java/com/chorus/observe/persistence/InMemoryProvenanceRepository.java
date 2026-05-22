package com.chorus.observe.persistence;

import com.chorus.observe.model.ProvenanceEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InMemoryProvenanceRepository extends ProvenanceRepository {
    private final List<ProvenanceEntry> store = new ArrayList<>();

    public InMemoryProvenanceRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(ProvenanceEntry entry) {
        store.removeIf(e -> e.entryId().equals(entry.entryId()));
        store.add(entry);
    }

    @Override
    public List<ProvenanceEntry> findByRunId(String runId) {
        return store.stream()
            .filter(e -> e.runId().equals(runId))
            .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
            .collect(Collectors.toList());
    }

    @Override
    public List<ProvenanceEntry> findByRunId(String runId, int limit, int offset) {
        return store.stream()
            .filter(e -> e.runId().equals(runId))
            .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
            .skip(offset).limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public long countByRunId(String runId) {
        return store.stream().filter(e -> e.runId().equals(runId)).count();
    }
}
