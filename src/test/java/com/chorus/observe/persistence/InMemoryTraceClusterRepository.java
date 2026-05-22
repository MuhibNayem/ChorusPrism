package com.chorus.observe.persistence;

import com.chorus.observe.model.TraceCluster;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class InMemoryTraceClusterRepository extends TraceClusterRepository {
    private final Map<String, TraceCluster> store = new HashMap<>();

    public InMemoryTraceClusterRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void save(TraceCluster cluster) {
        store.put(cluster.clusterId(), cluster);
    }

    @Override
    public Optional<TraceCluster> findById(String clusterId) {
        return Optional.ofNullable(store.get(clusterId));
    }

    @Override
    public List<TraceCluster> findAll() {
        return store.values().stream().sorted(Comparator.comparing(TraceCluster::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<TraceCluster> findAll(int limit, int offset) {
        return store.values().stream().sorted(Comparator.comparing(TraceCluster::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public List<TraceCluster> findByPeriod(Instant start, Instant end) {
        return store.values().stream().filter(c -> !c.periodStart().isBefore(start) && !c.periodEnd().isAfter(end)).sorted(Comparator.comparingInt(TraceCluster::runCount).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<TraceCluster> findByPeriod(Instant start, Instant end, int limit, int offset) {
        return store.values().stream().filter(c -> !c.periodStart().isBefore(start) && !c.periodEnd().isAfter(end)).sorted(Comparator.comparingInt(TraceCluster::runCount).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByPeriod(Instant start, Instant end) {
        return store.values().stream().filter(c -> !c.periodStart().isBefore(start) && !c.periodEnd().isAfter(end)).count();
    }
}
