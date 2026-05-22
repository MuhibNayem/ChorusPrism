package com.chorus.observe.persistence;

import com.chorus.observe.model.DatasetItem;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryDatasetItemRepository extends DatasetItemRepository {
    private final Map<String, DatasetItem> store = new HashMap<>();

    public InMemoryDatasetItemRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(DatasetItem item) {
        store.put(item.itemId(), item);
    }

    @Override
    public Optional<DatasetItem> findById(String itemId) {
        return Optional.ofNullable(store.get(itemId));
    }

    @Override
    public List<DatasetItem> findByDatasetId(String datasetId) {
        return store.values().stream().filter(i -> i.datasetId().equals(datasetId)).sorted(Comparator.comparing(DatasetItem::createdAt)).collect(Collectors.toList());
    }

    @Override
    public List<DatasetItem> findByDatasetId(String datasetId, int limit, int offset) {
        return store.values().stream().filter(i -> i.datasetId().equals(datasetId)).sorted(Comparator.comparing(DatasetItem::createdAt)).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public void deleteById(String itemId) {
        store.remove(itemId);
    }

    @Override
    public void deleteByDatasetId(String datasetId) {
        store.values().removeIf(i -> i.datasetId().equals(datasetId));
    }

    @Override
    public long countByDatasetId(String datasetId) {
        return store.values().stream().filter(i -> i.datasetId().equals(datasetId)).count();
    }
}
