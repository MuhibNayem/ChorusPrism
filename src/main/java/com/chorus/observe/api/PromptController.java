package com.chorus.observe.api;

import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.PromptAbTest;
import com.chorus.observe.model.PromptTag;
import com.chorus.observe.model.PromptVersion;
import com.chorus.observe.prompt.PromptAbTestExecutor;
import com.chorus.observe.service.PromptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for prompt registry and A/B testing.
 */
@RestController
@RequestMapping("/api/v1/prompts")
public class PromptController {

    private final PromptService promptService;
    private final PromptAbTestExecutor abTestExecutor;

    public PromptController(@NonNull PromptService promptService, @Nullable PromptAbTestExecutor abTestExecutor) {
        this.promptService = Objects.requireNonNull(promptService);
        this.abTestExecutor = abTestExecutor;
    }

    @PostMapping
    public ResponseEntity<PromptVersion> createVersion(@RequestBody @Valid @NonNull CreateVersionRequest request) {
        PromptVersion version = promptService.createVersion(request.name(), request.content(), request.model(), request.temperature(), request.maxTokens(), request.createdBy());
        return ResponseEntity.ok(version);
    }

    @GetMapping
    public ResponseEntity<PagedResult<PromptVersion>> listVersions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(promptService.listVersions(page, size));
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<PromptVersion> getVersion(@PathVariable @NonNull String versionId) {
        return promptService.getVersion(versionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{versionId}")
    public ResponseEntity<Void> deleteVersion(@PathVariable @NonNull String versionId) {
        promptService.deleteVersion(versionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{versionId}/tags")
    public ResponseEntity<PromptTag> addTag(@PathVariable @NonNull String versionId, @RequestBody @Valid @NonNull AddTagRequest request) {
        return ResponseEntity.ok(promptService.addTag(versionId, request.tagName()));
    }

    @DeleteMapping("/{versionId}/tags/{tagName}")
    public ResponseEntity<Void> removeTag(@PathVariable @NonNull String versionId, @PathVariable @NonNull String tagName) {
        promptService.removeTag(versionId, tagName);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{versionId}/tags")
    public ResponseEntity<PagedResult<PromptTag>> getTags(
            @PathVariable @NonNull String versionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(promptService.getTags(versionId, page, size));
    }

    @PostMapping("/ab-tests")
    public ResponseEntity<PromptAbTest> createAbTest(@RequestBody @Valid @NonNull CreateAbTestRequest request) {
        return ResponseEntity.ok(promptService.createAbTest(request.datasetId(), request.promptAId(), request.promptBId()));
    }

    @GetMapping("/ab-tests")
    public ResponseEntity<PagedResult<PromptAbTest>> listAbTests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(promptService.listAbTests(page, size));
    }

    @GetMapping("/ab-tests/{testId}")
    public ResponseEntity<PromptAbTest> getAbTest(@PathVariable @NonNull String testId) {
        return promptService.getAbTest(testId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/ab-tests/{testId}/complete")
    public ResponseEntity<Void> completeAbTest(@PathVariable @NonNull String testId, @RequestBody @Valid @NonNull CompleteAbTestRequest request) {
        promptService.completeAbTest(testId, request.winnerId(), request.pValue(), request.summaryMetrics());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/ab-tests/{testId}")
    public ResponseEntity<Void> deleteAbTest(@PathVariable @NonNull String testId) {
        promptService.deleteAbTest(testId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ab-tests/{testId}/execute")
    public ResponseEntity<PromptAbTestExecutor.AbTestResult> executeAbTest(@PathVariable @NonNull String testId) {
        if (abTestExecutor == null) {
            return ResponseEntity.status(503).build();
        }
        PromptAbTestExecutor.AbTestResult result = abTestExecutor.execute(testId);
        return ResponseEntity.ok(result);
    }

    public record CreateVersionRequest(@NotBlank String name, @NotBlank String content, String model, Double temperature, Integer maxTokens, String createdBy) {}
    public record AddTagRequest(@NotBlank String tagName) {}
    public record CreateAbTestRequest(String datasetId, @NotBlank String promptAId, @NotBlank String promptBId) {}
    public record CompleteAbTestRequest(String winnerId, Double pValue, @NotNull Map<String, Object> summaryMetrics) {}
}
