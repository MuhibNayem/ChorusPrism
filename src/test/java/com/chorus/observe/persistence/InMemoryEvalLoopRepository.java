package com.chorus.observe.persistence;

import com.chorus.observe.model.EvalLoop;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryEvalLoopRepository extends EvalLoopRepository {
    private final Map<String, EvalLoop> store = new HashMap<>();

    public InMemoryEvalLoopRepository() {
        super(null);
    }

    @Override
    public void save(EvalLoop loop) {
        store.put(loop.loopId(), loop);
    }

    @Override
    public Optional<EvalLoop> findById(String loopId) {
        return Optional.ofNullable(store.get(loopId));
    }

    @Override
    public List<EvalLoop> findAll() {
        return store.values().stream()
            .sorted(Comparator.comparing(EvalLoop::loopId))
            .collect(Collectors.toList());
    }

    @Override
    public List<EvalLoop> findByAgentId(String agentId) {
        return store.values().stream()
            .filter(e -> e.agentId().equals(agentId))
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String loopId) {
        store.remove(loopId);
    }
}
