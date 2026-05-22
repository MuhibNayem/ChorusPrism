package com.chorus.observe.persistence;

import com.chorus.observe.model.PromptAbTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryPromptAbTestRepository extends PromptAbTestRepository {
    private final Map<String, PromptAbTest> store = new HashMap<>();

    public InMemoryPromptAbTestRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(PromptAbTest test) {
        store.put(test.testId(), test);
    }

    @Override
    public Optional<PromptAbTest> findById(String testId) {
        return Optional.ofNullable(store.get(testId));
    }

    @Override
    public List<PromptAbTest> findAll() {
        return store.values().stream().sorted(Comparator.comparing(PromptAbTest::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<PromptAbTest> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(PromptAbTest::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public List<PromptAbTest> findByStatus(PromptAbTest.Status status) {
        return store.values().stream().filter(t -> t.status() == status).sorted(Comparator.comparing(PromptAbTest::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<PromptAbTest> findByStatus(PromptAbTest.Status status, int limit, int offset) {
        return store.values().stream().filter(t -> t.status() == status).sorted(Comparator.comparing(PromptAbTest::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(PromptAbTest.Status status) {
        return store.values().stream().filter(t -> t.status() == status).count();
    }

    @Override
    public void deleteById(String testId) {
        store.remove(testId);
    }
}
