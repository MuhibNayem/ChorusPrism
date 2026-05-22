package com.chorus.observe.persistence;

import com.chorus.observe.model.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InMemoryToolCallRepository extends ToolCallRepository {
    private final List<ToolCall> store = new ArrayList<>();

    public InMemoryToolCallRepository() {
        super(null);
    }

    @Override
    public void save(ToolCall call) {
        store.removeIf(c -> c.callId().equals(call.callId()));
        store.add(call);
    }

    @Override
    public List<ToolCall> findByRunId(String runId) {
        return store.stream()
            .filter(c -> c.runId().equals(runId))
            .collect(Collectors.toList());
    }

    @Override
    public List<ToolCall> findByRunId(String runId, int limit, int offset) {
        return store.stream()
            .filter(c -> c.runId().equals(runId))
            .skip(offset).limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public long countByRunId(String runId) {
        return store.stream().filter(c -> c.runId().equals(runId)).count();
    }

    @Override
    public List<ToolCall> findBySpanId(String spanId) {
        return store.stream()
            .filter(c -> c.spanId().equals(spanId))
            .collect(Collectors.toList());
    }
}
