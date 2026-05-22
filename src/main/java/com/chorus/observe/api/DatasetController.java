package com.chorus.observe.api;

import com.chorus.observe.model.Dataset;
import com.chorus.observe.model.DatasetItem;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.service.DatasetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for datasets.
 */
@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(@NonNull DatasetService datasetService) {
        this.datasetService = Objects.requireNonNull(datasetService);
    }

    @PostMapping
    public ResponseEntity<Dataset> createDataset(@RequestBody @NonNull CreateDatasetRequest request) {
        Dataset dataset = datasetService.createDataset(request.name(), request.description(), request.tags(), request.source());
        return ResponseEntity.ok(dataset);
    }

    @GetMapping
    public ResponseEntity<PagedResult<Dataset>> listDatasets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(datasetService.listDatasets(page, size));
    }

    @GetMapping("/{datasetId}")
    public ResponseEntity<Dataset> getDataset(@PathVariable @NonNull String datasetId) {
        return datasetService.getDataset(datasetId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{datasetId}")
    public ResponseEntity<Void> deleteDataset(@PathVariable @NonNull String datasetId) {
        datasetService.deleteDataset(datasetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{datasetId}/items")
    public ResponseEntity<DatasetItem> addItem(@PathVariable @NonNull String datasetId, @RequestBody @NonNull AddItemRequest request) {
        DatasetItem item = datasetService.addItem(datasetId, request.input(), request.expectedOutput(), request.metadata());
        return ResponseEntity.ok(item);
    }

    @GetMapping("/{datasetId}/items")
    public ResponseEntity<PagedResult<DatasetItem>> listItems(
            @PathVariable @NonNull String datasetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(datasetService.listItems(datasetId, page, size));
    }

    @PostMapping("/from-traces")
    public ResponseEntity<Dataset> createFromTraces(@RequestBody @NonNull CreateFromTracesRequest request) {
        Dataset dataset = datasetService.createFromTraces(request.name(), request.description(), request.runIds());
        return ResponseEntity.ok(dataset);
    }

    @PostMapping("/{datasetId}/import-jsonl")
    public ResponseEntity<List<DatasetItem>> importJsonl(@PathVariable @NonNull String datasetId, @RequestBody @NonNull List<String> lines) {
        return ResponseEntity.ok(datasetService.importJsonl(datasetId, lines));
    }

    @GetMapping("/{datasetId}/export-jsonl")
    public ResponseEntity<List<Map<String, Object>>> exportJsonl(@PathVariable @NonNull String datasetId) {
        return ResponseEntity.ok(datasetService.exportJsonl(datasetId));
    }

    public record CreateDatasetRequest(@NotBlank String name, String description, Map<String, String> tags, String source) {}
    public record AddItemRequest(@NotBlank String input, String expectedOutput, Map<String, Object> metadata) {}
    public record CreateFromTracesRequest(@NotBlank String name, String description, @NotNull @Size(max = 1000) List<String> runIds) {}
}
