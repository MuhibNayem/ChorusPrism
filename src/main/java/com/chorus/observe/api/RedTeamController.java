package com.chorus.observe.api;

import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.RedTeamResult;
import com.chorus.observe.model.RedTeamRun;
import com.chorus.observe.model.RedTeamScenario;
import com.chorus.observe.service.IdempotencyService;
import com.chorus.observe.service.RedTeamService;
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
 * REST API v1 for red teaming.
 */
@RestController
@RequestMapping("/api/v1/red-team")
public class RedTeamController {

    private final RedTeamService redTeamService;
    private final IdempotencyService idempotencyService;

    public RedTeamController(@NonNull RedTeamService redTeamService, @Nullable IdempotencyService idempotencyService) {
        this.redTeamService = Objects.requireNonNull(redTeamService);
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/scenarios")
    public ResponseEntity<RedTeamScenario> createScenario(@RequestBody @Valid @NonNull CreateScenarioRequest request) {
        RedTeamScenario scenario = redTeamService.createScenario(request.name(), request.category(), request.attackPrompt(), request.expectedBehavior(), request.severity());
        return ResponseEntity.ok(scenario);
    }

    @GetMapping("/scenarios")
    public ResponseEntity<PagedResult<RedTeamScenario>> listScenarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(redTeamService.listScenarios(page, size));
    }

    @GetMapping("/scenarios/{scenarioId}")
    public ResponseEntity<RedTeamScenario> getScenario(@PathVariable @NonNull String scenarioId) {
        return redTeamService.getScenario(scenarioId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/runs")
    public ResponseEntity<RedTeamRun> submitRun(
            @RequestBody @Valid @NonNull SubmitRedTeamRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && idempotencyService != null) {
            String existing = idempotencyService.checkOrRecord(idempotencyKey, "pending");
            if (existing != null) {
                return ResponseEntity.status(409).build();
            }
        }
        RedTeamRun run = redTeamService.submitRedTeamRun(request.agentConfig());
        if (idempotencyKey != null && !idempotencyKey.isBlank() && idempotencyService != null) {
            idempotencyService.checkOrRecord(idempotencyKey, run.redTeamRunId());
        }
        redTeamService.startRedTeamRun(run.redTeamRunId());
        return ResponseEntity.accepted().body(run);
    }

    @GetMapping("/runs")
    public ResponseEntity<PagedResult<RedTeamRun>> listRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(redTeamService.listRedTeamRuns(page, size));
    }

    @GetMapping("/runs/{redTeamRunId}")
    public ResponseEntity<RedTeamRun> getRun(@PathVariable @NonNull String redTeamRunId) {
        return redTeamService.getRedTeamRun(redTeamRunId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/runs/{redTeamRunId}/results")
    public ResponseEntity<PagedResult<RedTeamResult>> getResults(
            @PathVariable @NonNull String redTeamRunId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(redTeamService.getRedTeamResults(redTeamRunId, page, size));
    }

    @PostMapping("/runs/{id}/cancel")
    public ResponseEntity<Void> cancelRedTeamRun(@PathVariable @NonNull String id) {
        redTeamService.cancelRedTeamRun(id);
        return ResponseEntity.accepted().build();
    }

    public record CreateScenarioRequest(@NotBlank String name, @NotBlank String category, @NotBlank String attackPrompt, String expectedBehavior, @NotNull RedTeamScenario.Severity severity) {}
    public record SubmitRedTeamRequest(@NotNull Map<String, Object> agentConfig) {}
}
