package com.chorus.observe.persistence;

import com.chorus.observe.model.Dataset;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryDatasetRepository extends DatasetRepository {
    private final Map<String, Dataset> store = new HashMap<>();

    public InMemoryDatasetRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(Dataset dataset) {
        store.put(dataset.datasetId(), dataset);
    }

    @Override
    public Optional<Dataset> findById(String datasetId) {
        return Optional.ofNullable(store.get(datasetId));
    }

    @Override
    public List<Dataset> findAll() {
        return store.values().stream().sorted(Comparator.comparing(Dataset::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<Dataset> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(Dataset::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public List<Dataset> findBySource(String source) {
        return store.values().stream().filter(d -> d.source().equals(source)).sorted(Comparator.comparing(Dataset::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<Dataset> findBySource(String source, int limit, int offset) {
        return store.values().stream().filter(d -> d.source().equals(source)).sorted(Comparator.comparing(Dataset::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countBySource(String source) {
        return store.values().stream().filter(d -> d.source().equals(source)).count();
    }

    @Override
    public void deleteById(String datasetId) {
        store.remove(datasetId);
    }
}
