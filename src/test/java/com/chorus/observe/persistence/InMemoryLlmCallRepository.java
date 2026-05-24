package com.chorus.observe.persistence;

import com.chorus.observe.model.LlmCall;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InMemoryLlmCallRepository extends LlmCallRepository {
    private final List<LlmCall> store = new ArrayList<>();

    public InMemoryLlmCallRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(LlmCall call) {
        store.removeIf(c -> c.callId().equals(call.callId()));
        store.add(call);
    }

    @Override
    public void saveAll(List<LlmCall> calls) {
        for (LlmCall call : calls) {
            save(call);
        }
    }

    @Override
    public List<LlmCall> findByRunId(String runId) {
        return store.stream()
            .filter(c -> c.runId().equals(runId))
            .collect(Collectors.toList());
    }

    @Override
    public List<LlmCall> findByRunId(String runId, int limit, int offset) {
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
    public List<LlmCall> findBySpanId(String spanId) {
        return store.stream()
            .filter(c -> c.spanId().equals(spanId))
            .collect(Collectors.toList());
    }
}
