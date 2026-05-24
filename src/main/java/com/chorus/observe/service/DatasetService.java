package com.chorus.observe.service;

import com.chorus.observe.model.Dataset;
import com.chorus.observe.model.DatasetItem;
import com.chorus.observe.model.LlmCall;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.Run;
import com.chorus.observe.persistence.DatasetItemRepository;
import com.chorus.observe.persistence.DatasetRepository;
import com.chorus.observe.persistence.LlmCallRepository;
import com.chorus.observe.persistence.RunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for dataset management.
 */
public class DatasetService /* TEST MARK */ {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final DatasetItemRepository datasetItemRepository;
    private final RunRepository runRepository;
    private final LlmCallRepository llmCallRepository;
    private final ObjectMapper mapper;

    public DatasetService(
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository,
            @NonNull RunRepository runRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull ObjectMapper mapper) {
        this.datasetRepository = Objects.requireNonNull(datasetRepository);
        this.datasetItemRepository = Objects.requireNonNull(datasetItemRepository);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.llmCallRepository = Objects.requireNonNull(llmCallRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Timed(value = "dataset.create", description = "Time spent creating a dataset")
    @Counted(value = "dataset.create.count", description = "Total number of dataset creations")
    public @NonNull Dataset createDataset(@NonNull String name, @Nullable String description, @NonNull Map<String, String> tags, @NonNull String source) {
        String datasetId = "ds-" + UUID.randomUUID().toString().substring(0, 8);
        Dataset dataset = new Dataset(datasetId, name, description, tags, source, Map.of(), Instant.now(), Instant.now());
        datasetRepository.save(dataset);
        return dataset;
    }

    public @NonNull Optional<Dataset> getDataset(@NonNull String datasetId) {
        return datasetRepository.findById(datasetId);
    }

    public @NonNull List<Dataset> listDatasets() {
        return datasetRepository.findAll();
    }

    public @NonNull PagedResult<Dataset> listDatasets(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(datasetRepository.findAll(size, offset), datasetRepository.count(), page, size);
    }

    public @NonNull List<Dataset> listDatasetsBySource(@NonNull String source) {
        return datasetRepository.findBySource(source);
    }

    public @NonNull PagedResult<Dataset> listDatasetsBySource(@NonNull String source, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(datasetRepository.findBySource(source, size, offset), datasetRepository.countBySource(source), page, size);
    }

    @Transactional
    public void deleteDataset(@NonNull String datasetId) {
        datasetItemRepository.deleteByDatasetId(datasetId);
        datasetRepository.deleteById(datasetId);
    }

    public @NonNull DatasetItem addItem(@NonNull String datasetId, @NonNull String input, @Nullable String expectedOutput, @NonNull Map<String, Object> metadata) {
        String itemId = "item-" + UUID.randomUUID().toString().substring(0, 8);
        DatasetItem item = new DatasetItem(itemId, datasetId, input, expectedOutput, metadata, Map.of(), Instant.now());
        datasetItemRepository.save(item);
        return item;
    }

    public @NonNull List<DatasetItem> listItems(@NonNull String datasetId) {
        return datasetItemRepository.findByDatasetId(datasetId);
    }

    public @NonNull PagedResult<DatasetItem> listItems(@NonNull String datasetId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(datasetItemRepository.findByDatasetId(datasetId, size, offset), datasetItemRepository.countByDatasetId(datasetId), page, size);
    }

    public @NonNull Optional<DatasetItem> getItem(@NonNull String itemId) {
        return datasetItemRepository.findById(itemId);
    }

    public void deleteItem(@NonNull String itemId) {
        datasetItemRepository.deleteById(itemId);
    }

    public long countItems(@NonNull String datasetId) {
        return datasetItemRepository.countByDatasetId(datasetId);
    }

    @Timed(value = "dataset.createFromTraces", description = "Time spent creating a dataset from traces")
    @Counted(value = "dataset.createFromTraces.count", description = "Total number of dataset-from-traces creations")
    public @NonNull Dataset createFromTraces(@NonNull String name, @Nullable String description, @NonNull List<String> runIds) {
        Dataset dataset = createDataset(name, description, Map.of(), "traces");
        List<String> missingRuns = new ArrayList<>();
        List<String> emptyRuns = new ArrayList<>();
        int created = 0;

        for (String runId : runIds) {
            Optional<Run> runOpt = runRepository.findById(runId);
            if (runOpt.isEmpty()) {
                missingRuns.add(runId);
                LOG.warn("Run {} not found during dataset creation; skipping", runId);
                continue;
            }
            List<LlmCall> calls = llmCallRepository.findByRunId(runId);
            if (calls.isEmpty()) {
                emptyRuns.add(runId);
                LOG.warn("Run {} has no LLM calls during dataset creation; skipping", runId);
                continue;
            }
            String input = calls.get(0).prompt() != null ? calls.get(0).prompt() : "";
            String expected = calls.get(calls.size() - 1).completion() != null ? calls.get(calls.size() - 1).completion() : "";
            addItem(dataset.datasetId(), input, expected, Map.of("runId", runId));
            created++;
        }

        if (!missingRuns.isEmpty() || !emptyRuns.isEmpty()) {
            LOG.info("Dataset from traces: created {} items, {} missing runs, {} empty runs",
                created, missingRuns.size(), emptyRuns.size());
        }
        return dataset;
    }

    public @NonNull List<DatasetItem> importJsonl(@NonNull String datasetId, @NonNull List<String> lines) {
        List<DatasetItem> items = new ArrayList<>();
        for (String line : lines) {
            try {
                Map<String, Object> json = mapper.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                String input = Objects.toString(json.get("input"), "");
                String expected = Objects.toString(json.get("expected_output"), null);
                Map<String, Object> meta = json.containsKey("metadata") ? mapper.convertValue(json.get("metadata"), new com.fasterxml.jackson.core.type.TypeReference<>() {}) : Map.of();
                items.add(addItem(datasetId, input, expected, meta));
            } catch (Exception e) {
                LOG.warn("Failed to parse JSONL line: {}", line, e);
            }
        }
        return items;
    }

    public @NonNull List<DatasetItem> splitDataset(@NonNull String datasetId, @NonNull String splitName, double ratio) {
        List<DatasetItem> items = datasetItemRepository.findByDatasetId(datasetId);
        int count = (int) (items.size() * ratio);
        List<DatasetItem> split = items.subList(0, Math.min(count, items.size()));
        for (DatasetItem item : split) {
            Map<String, Object> meta = new HashMap<>(item.metadata());
            meta.put("split", splitName);
            DatasetItem updated = new DatasetItem(item.itemId(), item.datasetId(), item.input(), item.expectedOutput(), meta, item.tags(), item.createdAt());
            datasetItemRepository.save(updated);
        }
        return split;
    }

    public @NonNull List<Map<String, Object>> exportJsonl(@NonNull String datasetId) {
        return datasetItemRepository.findByDatasetId(datasetId).stream()
            .map(item -> Map.<String, Object>of(
                "input", item.input(),
                "expected_output", item.expectedOutput(),
                "metadata", item.metadata()
            ))
            .collect(Collectors.toList());
    }
}
