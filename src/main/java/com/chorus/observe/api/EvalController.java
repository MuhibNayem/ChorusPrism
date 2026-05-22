package com.chorus.observe.api;

import com.chorus.observe.model.EvalResultRecord;
import com.chorus.observe.model.EvalRun;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.service.EvalService;
import com.chorus.observe.service.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
 * REST API v1 for evaluation runs and results.
 */
@RestController
@RequestMapping("/api/v1/eval-runs")
public class EvalController {

    private final EvalService evalService;
    private final IdempotencyService idempotencyService;

    public EvalController(@NonNull EvalService evalService, @Nullable IdempotencyService idempotencyService) {
        this.evalService = Objects.requireNonNull(evalService);
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<EvalRun> submitEvalRun(
            @RequestBody @Valid @NonNull SubmitEvalRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && idempotencyService != null) {
            String existing = idempotencyService.checkOrRecord(idempotencyKey, "pending");
            if (existing != null) {
                return ResponseEntity.status(409).build();
            }
        }
        EvalRun evalRun = evalService.submitEvalRun(request.datasetId(), request.name(), request.agentConfig(), request.scorerConfig(), request.parallelism());
        if (idempotencyKey != null && !idempotencyKey.isBlank() && idempotencyService != null) {
            idempotencyService.checkOrRecord(idempotencyKey, evalRun.evalRunId());
        }
        evalService.startEvalRun(evalRun.evalRunId());
        return ResponseEntity.accepted().body(evalRun);
    }

    @GetMapping
    public ResponseEntity<PagedResult<EvalRun>> listEvalRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(evalService.listEvalRuns(page, size));
    }

    @GetMapping("/{evalRunId}")
    public ResponseEntity<EvalRun> getEvalRun(@PathVariable @NonNull String evalRunId) {
        return evalService.getEvalRun(evalRunId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{evalRunId}/results")
    public ResponseEntity<PagedResult<EvalResultRecord>> getEvalResults(
            @PathVariable @NonNull String evalRunId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(evalService.getEvalResults(evalRunId, page, size));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelEvalRun(@PathVariable @NonNull String id) {
        evalService.cancelEvalRun(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/compare")
    public ResponseEntity<EvalService.RegressionReport> compareRuns(
            @RequestParam @NonNull String runA,
            @RequestParam @NonNull String runB) {
        return ResponseEntity.ok(evalService.compareRuns(runA, runB));
    }

    public record SubmitEvalRequest(
        @NotBlank String datasetId,
        String name,
        @NotNull Map<String, Object> agentConfig,
        @NotNull Map<String, Object> scorerConfig,
        @Min(1) int parallelism
    ) {
        public SubmitEvalRequest {
            if (parallelism < 1) parallelism = 8;
        }
    }
}
