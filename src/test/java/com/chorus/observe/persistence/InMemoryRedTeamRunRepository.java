package com.chorus.observe.persistence;

import com.chorus.observe.model.RedTeamRun;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryRedTeamRunRepository extends RedTeamRunRepository {
    private final Map<String, RedTeamRun> store = new HashMap<>();

    public InMemoryRedTeamRunRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(RedTeamRun run) {
        store.put(run.redTeamRunId(), run);
    }

    @Override
    public Optional<RedTeamRun> findById(String redTeamRunId) {
        return Optional.ofNullable(store.get(redTeamRunId));
    }

    @Override
    public List<RedTeamRun> findAll() {
        return store.values().stream().sorted(Comparator.comparing(RedTeamRun::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<RedTeamRun> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(RedTeamRun::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public List<RedTeamRun> findByStatus(RedTeamRun.Status status) {
        return store.values().stream().filter(r -> r.status() == status).sorted(Comparator.comparing(RedTeamRun::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<RedTeamRun> findByStatus(RedTeamRun.Status status, int limit, int offset) {
        return store.values().stream().filter(r -> r.status() == status).sorted(Comparator.comparing(RedTeamRun::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(RedTeamRun.Status status) {
        return store.values().stream().filter(r -> r.status() == status).count();
    }
}
