package com.chorus.observe.persistence;

import com.chorus.observe.model.RagQuery;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class InMemoryRagQueryRepository extends RagQueryRepository {
    private final List<RagQuery> store = new ArrayList<>();

    public InMemoryRagQueryRepository() {
        super(new ObjectMapper());
    }

    @Override
    public void save(RagQuery query) {
        store.removeIf(q -> q.queryId().equals(query.queryId()));
        store.add(query);
    }

    @Override
    public List<RagQuery> findByRunId(String runId) {
        return store.stream().filter(q -> q.runId().equals(runId)).collect(Collectors.toList());
    }

    @Override
    public Optional<RagQuery> findByQueryId(String queryId) {
        return store.stream().filter(q -> q.queryId().equals(queryId)).findFirst();
    }

    @Override
    public List<RagQuery> listQueries(Instant from, Instant to, String collection, int offset, int limit) {
        return store.stream()
            .filter(q -> collection == null || collection.equals(q.collection()))
            .skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public long countQueries(Instant from, Instant to, String collection) {
        return store.stream()
            .filter(q -> collection == null || collection.equals(q.collection()))
            .count();
    }

    @Override
    public Map<String, Object> getAggregateMetrics(Instant from, Instant to, String collection) {
        return Map.of(
            "query_count", store.size(),
            "avg_latency_ms", 0L,
            "p50_latency_ms", 0L,
            "p95_latency_ms", 0L,
            "p99_latency_ms", 0L,
            "avg_context_precision", 0.0,
            "avg_context_recall", 0.0,
            "avg_faithfulness", 0.0,
            "avg_answer_relevancy", 0.0,
            "hit_rate", 0.0
        );
    }

    @Override
    public List<Map<String, Object>> getLatencyHistogram(Instant from, Instant to, String collection) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> getTopQueries(Instant from, Instant to, String collection, int limit) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> getTrend(Instant from, Instant to, String granularity, String collection) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> getCollectionBreakdown(Instant from, Instant to) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getCollections(Instant from, Instant to) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> getDriftSnapshots(Instant from, Instant to, String collection) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> findQueriesNeedingEmbedding(Instant from, Instant to, int limit) {
        return Collections.emptyList();
    }

    @Override
    public void updateScores(String queryId, Double faithfulness, Double answerRelevancy) {
        store.stream().filter(q -> q.queryId().equals(queryId)).findFirst().ifPresent(q -> {
            store.remove(q);
            store.add(new RagQuery(
                q.queryId(), q.spanId(), q.runId(), q.query(),
                q.retrievedChunks(), q.similarityScores(), q.latencyMs(), q.metadata(),
                q.contextPrecision(), q.contextRecall(),
                faithfulness != null ? faithfulness : q.faithfulness(),
                answerRelevancy != null ? answerRelevancy : q.answerRelevancy(),
                q.chunkCount(), q.collection(), q.topK()
            ));
        });
    }
}
