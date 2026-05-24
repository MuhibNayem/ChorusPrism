package com.chorus.observe.persistence;

import com.chorus.observe.model.Run;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory fake for RunRepository. Thread-safe for single-threaded tests.
 */
public class InMemoryRunRepository extends RunRepository {
    private final Map<String, Run> store = new HashMap<>();

    public InMemoryRunRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(Run run) {
        store.put(run.runId(), run);
    }

    @Override
    public Optional<Run> findById(String runId) {
        return Optional.ofNullable(store.get(runId));
    }

    @Override
    public List<Run> findAll(RunQuery query) {
        return store.values().stream()
            .filter(r -> query.tenantId() == null || r.tenantId().equals(query.tenantId()))
            .filter(r -> query.framework() == null || r.framework().equals(query.framework()))
            .filter(r -> query.agentId() == null || r.agentId().equals(query.agentId()))
            .filter(r -> query.status() == null || r.status() == query.status())
            .sorted((a, b) -> {
                int cmp = switch (query.sortBy()) {
                    case "start_time" -> a.startTime().compareTo(b.startTime());
                    case "run_id" -> a.runId().compareTo(b.runId());
                    default -> a.startTime().compareTo(b.startTime());
                };
                return "ASC".equalsIgnoreCase(query.sortOrder()) ? cmp : -cmp;
            })
            .skip(query.offset())
            .limit(query.limit())
            .collect(Collectors.toList());
    }

    @Override
    public long count(RunQuery query) {
        return findAll(new RunQuery(query.tenantId(), query.framework(), query.agentId(), null, query.status(), null, null, null, null, null, "start_time", "ASC", Integer.MAX_VALUE, 0)).size();
    }

    @Override
    public boolean exists(String runId) {
        return store.containsKey(runId);
    }

    @Override
    public Optional<Run> findByIdAndTenantId(String runId, String tenantId) {
        return Optional.ofNullable(store.get(runId)).filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public boolean existsForTenant(String runId, String tenantId) {
        return store.containsKey(runId) && store.get(runId).tenantId().equals(tenantId);
    }
}
