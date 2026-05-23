package com.chorus.observe.api;

import com.chorus.observe.model.Evaluator;
import com.chorus.observe.model.RunEvaluation;
import com.chorus.observe.service.EvaluatorService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for evaluators and run evaluations.
 */
@RestController
@RequestMapping("/api/v1")
public class EvaluatorController {

    private final EvaluatorService evaluatorService;

    public EvaluatorController(@NonNull EvaluatorService evaluatorService) {
        this.evaluatorService = Objects.requireNonNull(evaluatorService);
    }

    @GetMapping("/evaluators")
    public ResponseEntity<List<EvaluatorService.EvaluatorWithScore>> listEvaluators() {
        return ResponseEntity.ok(evaluatorService.listEvaluators());
    }

    @GetMapping("/evaluators/{evaluatorId}")
    public ResponseEntity<Evaluator> getEvaluator(@PathVariable @NonNull String evaluatorId) {
        return evaluatorService.getEvaluator(evaluatorId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/evaluators")
    public ResponseEntity<Evaluator> createEvaluator(@RequestBody @NonNull CreateEvaluatorRequest request) {
        Evaluator evaluator = evaluatorService.createEvaluator(
            request.name(), request.kind(), request.description(), request.config());
        return ResponseEntity.ok(evaluator);
    }

    @GetMapping("/runs/{runId}/evaluations")
    public ResponseEntity<List<RunEvaluation>> getRunEvaluations(@PathVariable @NonNull String runId) {
        return ResponseEntity.ok(evaluatorService.getRunEvaluations(runId));
    }

    @PostMapping("/runs/{runId}/evaluate")
    public ResponseEntity<RunEvaluation> evaluateRun(
            @PathVariable @NonNull String runId,
            @RequestBody @NonNull EvaluateRunRequest request) {
        return ResponseEntity.ok(evaluatorService.evaluateRun(runId, request.evaluatorId()));
    }

    public record CreateEvaluatorRequest(
        @NonNull String name,
        @NonNull String kind,
        @Nullable String description,
        @NonNull Map<String, Object> config
    ) {}

    public record EvaluateRunRequest(
        @NonNull String evaluatorId
    ) {}
}
