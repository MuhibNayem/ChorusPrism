package com.chorus.observe.persistence;

import com.chorus.observe.model.RagQuery;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InMemoryRagQueryRepository extends RagQueryRepository {
    private final List<RagQuery> store = new ArrayList<>();

    public InMemoryRagQueryRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(RagQuery query) {
        store.removeIf(q -> q.queryId().equals(query.queryId()));
        store.add(query);
    }

    @Override
    public List<RagQuery> findByRunId(String runId) {
        return store.stream()
            .filter(q -> q.runId().equals(runId))
            .collect(Collectors.toList());
    }
}
