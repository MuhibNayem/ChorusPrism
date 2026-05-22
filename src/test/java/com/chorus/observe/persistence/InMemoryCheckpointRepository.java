package com.chorus.observe.persistence;

import com.chorus.observe.model.Checkpoint;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryCheckpointRepository extends CheckpointRepository {
    private final Map<String, Checkpoint> store = new HashMap<>();

    public InMemoryCheckpointRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(Checkpoint checkpoint) {
        store.put(checkpoint.checkpointId(), checkpoint);
    }

    @Override
    public Optional<Checkpoint> findById(String checkpointId) {
        return Optional.ofNullable(store.get(checkpointId));
    }

    @Override
    public List<Checkpoint> findByRunId(String runId) {
        return store.values().stream().filter(c -> c.runId().equals(runId)).sorted(Comparator.comparingInt(Checkpoint::sequence)).collect(Collectors.toList());
    }

    @Override
    public List<Checkpoint> findByRunId(String runId, int limit, int offset) {
        return store.values().stream().filter(c -> c.runId().equals(runId)).sorted(Comparator.comparingInt(Checkpoint::sequence)).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByRunId(String runId) {
        return store.values().stream().filter(c -> c.runId().equals(runId)).count();
    }

    @Override
    public Optional<Checkpoint> findByRunIdAndSequence(String runId, int sequence) {
        return store.values().stream().filter(c -> c.runId().equals(runId) && c.sequence() == sequence).findFirst();
    }

    @Override
    public Optional<Checkpoint> findLatestByRunId(String runId) {
        return store.values().stream().filter(c -> c.runId().equals(runId)).max(Comparator.comparingInt(Checkpoint::sequence));
    }

    @Override
    public void deleteByRunId(String runId) {
        store.values().removeIf(c -> c.runId().equals(runId));
    }
}
