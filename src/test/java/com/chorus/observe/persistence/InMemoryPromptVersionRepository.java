package com.chorus.observe.persistence;

import com.chorus.observe.model.PromptVersion;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryPromptVersionRepository extends PromptVersionRepository {
    private final Map<String, PromptVersion> store = new HashMap<>();

    public InMemoryPromptVersionRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(PromptVersion prompt) {
        store.put(prompt.versionId(), prompt);
    }

    @Override
    public Optional<PromptVersion> findById(String versionId) {
        return Optional.ofNullable(store.get(versionId));
    }

    @Override
    public List<PromptVersion> findByName(String name) {
        return store.values().stream().filter(p -> p.name().equals(name)).sorted(Comparator.comparing(PromptVersion::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<PromptVersion> findByName(String name, int limit, int offset) {
        return store.values().stream().filter(p -> p.name().equals(name)).sorted(Comparator.comparing(PromptVersion::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByName(String name) {
        return store.values().stream().filter(p -> p.name().equals(name)).count();
    }

    @Override
    public List<PromptVersion> findAll() {
        return store.values().stream().sorted(Comparator.comparing(PromptVersion::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<PromptVersion> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(PromptVersion::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public void deleteById(String versionId) {
        store.remove(versionId);
    }
}
