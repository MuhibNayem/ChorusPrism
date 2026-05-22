package com.chorus.observe.persistence;

import com.chorus.observe.model.ReplayRun;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryReplayRunRepository extends ReplayRunRepository {
    private final Map<String, ReplayRun> store = new HashMap<>();

    public InMemoryReplayRunRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(ReplayRun replayRun) {
        store.put(replayRun.replayRunId(), replayRun);
    }

    @Override
    public Optional<ReplayRun> findById(String replayRunId) {
        return Optional.ofNullable(store.get(replayRunId));
    }

    @Override
    public List<ReplayRun> findByOriginalRunId(String originalRunId) {
        return store.values().stream().filter(r -> r.originalRunId().equals(originalRunId)).sorted(Comparator.comparing(ReplayRun::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<ReplayRun> findByOriginalRunId(String originalRunId, int limit, int offset) {
        return store.values().stream().filter(r -> r.originalRunId().equals(originalRunId)).sorted(Comparator.comparing(ReplayRun::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByOriginalRunId(String originalRunId) {
        return store.values().stream().filter(r -> r.originalRunId().equals(originalRunId)).count();
    }

    @Override
    public List<ReplayRun> findAll() {
        return store.values().stream().sorted(Comparator.comparing(ReplayRun::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<ReplayRun> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(ReplayRun::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }
}
