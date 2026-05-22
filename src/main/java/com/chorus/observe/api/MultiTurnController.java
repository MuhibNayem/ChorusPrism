package com.chorus.observe.api;

import com.chorus.observe.model.MultiTurnRun;
import com.chorus.observe.model.MultiTurnScenario;
import com.chorus.observe.model.MultiTurnTurn;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.service.MultiTurnTestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for multi-turn conversation testing.
 */
@RestController
@RequestMapping("/api/v1/multi-turn")
public class MultiTurnController {

    private final MultiTurnTestService multiTurnTestService;

    public MultiTurnController(@NonNull MultiTurnTestService multiTurnTestService) {
        this.multiTurnTestService = Objects.requireNonNull(multiTurnTestService);
    }

    @PostMapping("/scenarios")
    public ResponseEntity<MultiTurnScenario> createScenario(@RequestBody @Valid @NonNull CreateScenarioRequest request) {
        return ResponseEntity.ok(multiTurnTestService.createScenario(request.name(), request.description(), request.turns()));
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<MultiTurnScenario>> listScenarios() {
        return ResponseEntity.ok(multiTurnTestService.listScenarios());
    }

    @GetMapping("/scenarios/{scenarioId}")
    public ResponseEntity<MultiTurnScenario> getScenario(@PathVariable @NonNull String scenarioId) {
        return multiTurnTestService.getScenario(scenarioId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/runs")
    public ResponseEntity<MultiTurnRun> submitRun(@RequestBody @Valid @NonNull SubmitRunRequest request) {
        MultiTurnRun run = multiTurnTestService.submitRun(request.scenarioId(), request.agentConfig());
        multiTurnTestService.startRun(run.runId());
        return ResponseEntity.accepted().body(run);
    }

    @GetMapping("/runs")
    public ResponseEntity<List<MultiTurnRun>> listRuns() {
        return ResponseEntity.ok(multiTurnTestService.listRuns());
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<MultiTurnRun> getRun(@PathVariable @NonNull String runId) {
        return multiTurnTestService.getRun(runId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/runs/{runId}/turns")
    public ResponseEntity<List<MultiTurnTurn>> getTurns(@PathVariable @NonNull String runId) {
        return ResponseEntity.ok(multiTurnTestService.getTurns(runId));
    }

    public record CreateScenarioRequest(@NotBlank String name, String description, @NonNull List<MultiTurnScenario.Turn> turns) {}
    public record SubmitRunRequest(@NotBlank String scenarioId, @NonNull Map<String, Object> agentConfig) {}
}
