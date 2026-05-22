package com.chorus.observe.persistence;

import com.chorus.observe.model.RedTeamResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryRedTeamResultRepository extends RedTeamResultRepository {
    private final Map<String, RedTeamResult> store = new HashMap<>();

    public InMemoryRedTeamResultRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(RedTeamResult result) {
        store.put(result.resultId(), result);
    }

    @Override
    public Optional<RedTeamResult> findById(String resultId) {
        return Optional.ofNullable(store.get(resultId));
    }

    @Override
    public List<RedTeamResult> findByRedTeamRunId(String redTeamRunId) {
        return store.values().stream().filter(r -> r.redTeamRunId().equals(redTeamRunId)).sorted(Comparator.comparing(RedTeamResult::createdAt)).collect(Collectors.toList());
    }

    @Override
    public List<RedTeamResult> findByRedTeamRunId(String redTeamRunId, int limit, int offset) {
        return store.values().stream().filter(r -> r.redTeamRunId().equals(redTeamRunId)).sorted(Comparator.comparing(RedTeamResult::createdAt)).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByRedTeamRunId(String redTeamRunId) {
        return store.values().stream().filter(r -> r.redTeamRunId().equals(redTeamRunId)).count();
    }

    @Override
    public long countBypassedByRunId(String redTeamRunId) {
        return store.values().stream().filter(r -> r.redTeamRunId().equals(redTeamRunId) && r.bypassed()).count();
    }
}
