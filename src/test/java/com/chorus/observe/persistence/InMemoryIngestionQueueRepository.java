package com.chorus.observe.persistence;

import com.chorus.observe.service.OtlpIngestionService.OtlpSpan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class InMemoryIngestionQueueRepository extends IngestionQueueRepository {
    private final List<QueueRecord> store = new ArrayList<>();

    public InMemoryIngestionQueueRepository() {
        super(null, new ObjectMapper());
    }

    @Override
    public void enqueue(@NonNull List<OtlpSpan> spans) {
        for (OtlpSpan span : spans) {
            String queueId = "q-" + UUID.randomUUID().toString();
            store.add(new QueueRecord(queueId, span));
        }
    }

    @Override
    public @NonNull List<QueueRecord> fetchBatch(int limit) {
        return store.stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public void dequeue(@NonNull List<String> queueIds) {
        store.removeIf(record -> queueIds.contains(record.queueId()));
    }
}
