package com.chorus.observe.api;

import com.chorus.observe.model.Breakpoint;
import com.chorus.observe.model.Checkpoint;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.ReplayRun;
import com.chorus.observe.service.TimeTravelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for time-travel debugging.
 */
@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/api/v1/runs/{runId}")
public class TimeTravelController {

    private final TimeTravelService timeTravelService;

    public TimeTravelController(@NonNull TimeTravelService timeTravelService) {
        this.timeTravelService = Objects.requireNonNull(timeTravelService);
    }

    @GetMapping("/checkpoints")
    public ResponseEntity<PagedResult<Checkpoint>> getCheckpoints(
            @PathVariable @NonNull String runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(timeTravelService.getCheckpoints(runId, page, size));
    }

    @PostMapping("/checkpoints")
    public ResponseEntity<Checkpoint> saveCheckpoint(@PathVariable @NonNull String runId, @RequestBody @Valid @NonNull SaveCheckpointRequest request) {
        Checkpoint checkpoint = timeTravelService.saveCheckpoint(runId, request.sequence(), request.stateSnapshot(), request.nextNodes());
        return ResponseEntity.ok(checkpoint);
    }

    @GetMapping("/checkpoints/latest")
    public ResponseEntity<Checkpoint> getLatestCheckpoint(@PathVariable @NonNull String runId) {
        return timeTravelService.getLatestCheckpoint(runId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/replay")
    public ResponseEntity<ReplayRun> createReplay(@PathVariable @NonNull String runId, @RequestBody @Valid @NonNull CreateReplayRequest request) {
        ReplayRun replay = timeTravelService.createReplay(runId, request.fromCheckpointId(), request.stateOverrides());
        return ResponseEntity.ok(replay);
    }

    @GetMapping("/replays")
    public ResponseEntity<PagedResult<ReplayRun>> getReplays(
            @PathVariable @NonNull String runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(timeTravelService.getReplayRuns(runId, page, size));
    }

    @PostMapping("/breakpoints")
    public ResponseEntity<Breakpoint> createBreakpoint(@PathVariable @NonNull String runId, @RequestBody @Valid @NonNull CreateBreakpointRequest request) {
        Breakpoint breakpoint = timeTravelService.createBreakpoint(runId, request.beforeNode(), request.beforeTool());
        return ResponseEntity.ok(breakpoint);
    }

    @GetMapping("/breakpoints")
    public ResponseEntity<PagedResult<Breakpoint>> getBreakpoints(
            @PathVariable @NonNull String runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(timeTravelService.getBreakpoints(runId, page, size));
    }

    @PostMapping("/breakpoints/{breakpointId}/resolve")
    public ResponseEntity<Void> resolveBreakpoint(@PathVariable @NonNull String breakpointId) {
        timeTravelService.resolveBreakpoint(breakpointId);
        return ResponseEntity.noContent().build();
    }

    public record SaveCheckpointRequest(int sequence, @NotNull Map<String, Object> stateSnapshot, @NotNull List<String> nextNodes) {}
    public record CreateReplayRequest(String fromCheckpointId, @NotNull Map<String, Object> stateOverrides) {}
    public record CreateBreakpointRequest(String beforeNode, String beforeTool) {}
}
