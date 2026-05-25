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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
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
        Map<String, String> tags = new HashMap<>();
        if (request.tags() != null) {
            tags.putAll(request.tags());
        }
        // Inject owner into tags map so it is persisted
        if (request.owner() != null && !request.owner().isBlank()) {
            tags.put("owner", request.owner());
        }
        Dataset dataset = datasetService.createDataset(request.name(), request.description(), tags, request.source());
        return ResponseEntity.ok(dataset);
    }

    @GetMapping
    public ResponseEntity<PagedResult<DatasetSummary>> listDatasets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResult<Dataset> paged = datasetService.listDatasets(page, size);
        List<DatasetSummary> summaries = paged.items().stream()
            .map(d -> new DatasetSummary(d, datasetService.countItems(d.datasetId())))
            .toList();
        return ResponseEntity.ok(new PagedResult<>(summaries, paged.total(), paged.page(), paged.size()));
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

    public record CreateDatasetRequest(
        @NotBlank String name,
        String description,
        Map<String, String> tags,
        String source,
        String owner
    ) {}
    public record AddItemRequest(@NotBlank String input, String expectedOutput, Map<String, Object> metadata) {}
    public record CreateFromTracesRequest(@NotBlank String name, String description, @NotNull @Size(max = 1000) List<String> runIds) {}

    /**
     * Frontend-facing dataset summary with the contract the UI expects.
     * <ul>
     *   <li>{@code examples} — item count</li>
     *   <li>{@code updated} — human-readable relative time</li>
     *   <li>{@code owner} — from tags["owner"], fallback "platform"</li>
     *   <li>{@code tags} — tag keys excluding "owner"</li>
     * </ul>
     */
    public record DatasetSummary(
        String datasetId,
        String name,
        String description,
        long examples,
        String updated,
        String owner,
        List<String> tags
    ) {
        DatasetSummary(Dataset d, long itemCount) {
            this(
                d.datasetId(),
                d.name(),
                d.description(),
                itemCount,
                relativeTime(d.updatedAt()),
                extractOwner(d.tags()),
                extractTagKeys(d.tags())
            );
        }

        private static String relativeTime(Instant ts) {
            if (ts == null) return "—";
            long mins = ChronoUnit.MINUTES.between(ts, Instant.now());
            if (mins < 2) return "just now";
            if (mins < 60) return mins + "m ago";
            long hrs = mins / 60;
            if (hrs < 24) return hrs + "h ago";
            return (hrs / 24) + "d ago";
        }

        private static String extractOwner(Map<String, String> tags) {
            if (tags == null) return "platform";
            return tags.getOrDefault("owner", "platform");
        }

        private static List<String> extractTagKeys(Map<String, String> tags) {
            if (tags == null) return List.of();
            List<String> keys = new ArrayList<>();
            for (String key : tags.keySet()) {
                if (!"owner".equals(key)) {
                    keys.add(key);
                }
            }
            return keys;
        }
    }
}
