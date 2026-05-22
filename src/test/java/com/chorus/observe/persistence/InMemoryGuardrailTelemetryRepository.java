package com.chorus.observe.persistence;

import com.chorus.observe.model.GuardrailTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryGuardrailTelemetryRepository extends GuardrailTelemetryRepository {
    private final Map<String, GuardrailTelemetry> store = new HashMap<>();

    public InMemoryGuardrailTelemetryRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(GuardrailTelemetry telemetry) {
        store.put(telemetry.telemetryId(), telemetry);
    }

    @Override
    public Optional<GuardrailTelemetry> findById(String telemetryId) {
        return Optional.ofNullable(store.get(telemetryId));
    }

    @Override
    public List<GuardrailTelemetry> findByRunId(String runId) {
        return store.values().stream().filter(t -> runId.equals(t.runId())).sorted(Comparator.comparing(GuardrailTelemetry::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<GuardrailTelemetry> findByRunId(String runId, int limit, int offset) {
        return store.values().stream().filter(t -> runId.equals(t.runId())).sorted(Comparator.comparing(GuardrailTelemetry::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByRunId(String runId) {
        return store.values().stream().filter(t -> runId.equals(t.runId())).count();
    }

    @Override
    public List<GuardrailTelemetry> findByGuardrailName(String guardrailName) {
        return store.values().stream().filter(t -> t.guardrailName().equals(guardrailName)).sorted(Comparator.comparing(GuardrailTelemetry::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<GuardrailTelemetry> findByGuardrailName(String guardrailName, int limit, int offset) {
        return store.values().stream().filter(t -> t.guardrailName().equals(guardrailName)).sorted(Comparator.comparing(GuardrailTelemetry::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByGuardrailName(String guardrailName) {
        return store.values().stream().filter(t -> t.guardrailName().equals(guardrailName)).count();
    }

    @Override
    public List<GuardrailTelemetry> findRecent(int limit) {
        return store.values().stream().sorted(Comparator.comparing(GuardrailTelemetry::createdAt).reversed()).limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<GuardrailTelemetry> findRecent(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(GuardrailTelemetry::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }
}
