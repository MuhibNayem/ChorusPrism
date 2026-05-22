package com.chorus.observe.persistence;

import com.chorus.observe.model.Breakpoint;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryBreakpointRepository extends BreakpointRepository {
    private final Map<String, Breakpoint> store = new HashMap<>();

    public InMemoryBreakpointRepository() {
        super(null);
    }

    @Override
    public void save(Breakpoint breakpoint) {
        store.put(breakpoint.breakpointId(), breakpoint);
    }

    @Override
    public Optional<Breakpoint> findById(String breakpointId) {
        return Optional.ofNullable(store.get(breakpointId));
    }

    @Override
    public List<Breakpoint> findByRunId(String runId) {
        return store.values().stream().filter(b -> b.runId().equals(runId)).sorted(Comparator.comparing(Breakpoint::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<Breakpoint> findByRunId(String runId, int limit, int offset) {
        return store.values().stream().filter(b -> b.runId().equals(runId)).sorted(Comparator.comparing(Breakpoint::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByRunId(String runId) {
        return store.values().stream().filter(b -> b.runId().equals(runId)).count();
    }

    @Override
    public List<Breakpoint> findActiveByRunId(String runId) {
        return store.values().stream().filter(b -> b.runId().equals(runId) && b.status() == Breakpoint.Status.ACTIVE).sorted(Comparator.comparing(Breakpoint::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<Breakpoint> findActiveByRunId(String runId, int limit, int offset) {
        return store.values().stream().filter(b -> b.runId().equals(runId) && b.status() == Breakpoint.Status.ACTIVE).sorted(Comparator.comparing(Breakpoint::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countActiveByRunId(String runId) {
        return store.values().stream().filter(b -> b.runId().equals(runId) && b.status() == Breakpoint.Status.ACTIVE).count();
    }

    @Override
    public void deleteById(String breakpointId) {
        store.remove(breakpointId);
    }

    @Override
    public void deleteByRunId(String runId) {
        store.values().removeIf(b -> b.runId().equals(runId));
    }
}
