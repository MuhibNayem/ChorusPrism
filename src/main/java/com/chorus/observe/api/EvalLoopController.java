package com.chorus.observe.api;

import com.chorus.observe.model.EvalLoop;
import com.chorus.observe.service.EvalLoopService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for continuous evaluation loops.
 */
@RestController
@RequestMapping("/api/v1/eval-loops")
public class EvalLoopController {

    private final EvalLoopService evalLoopService;

    public EvalLoopController(@NonNull EvalLoopService evalLoopService) {
        this.evalLoopService = Objects.requireNonNull(evalLoopService);
    }

    @GetMapping
    public ResponseEntity<List<EvalLoop>> listLoops() {
        return ResponseEntity.ok(evalLoopService.listLoops());
    }

    @PostMapping
    public ResponseEntity<EvalLoop> createLoop(@RequestBody @NonNull CreateLoopRequest request) {
        EvalLoop loop = evalLoopService.createLoop(
            request.agentId(),
            request.evaluatorId(),
            request.samplingRate(),
            request.alertThreshold()
        );
        return ResponseEntity.ok(loop);
    }

    @PatchMapping("/{loopId}")
    public ResponseEntity<EvalLoop> toggleLoop(
            @PathVariable @NonNull String loopId,
            @RequestBody @NonNull Map<String, String> body) {
        String status = body.getOrDefault("status", "ACTIVE");
        return ResponseEntity.ok(evalLoopService.toggleLoop(loopId, status));
    }

    @DeleteMapping("/{loopId}")
    public ResponseEntity<Void> deleteLoop(@PathVariable @NonNull String loopId) {
        evalLoopService.deleteLoop(loopId);
        return ResponseEntity.noContent().build();
    }

    public record CreateLoopRequest(
        @NonNull String agentId,
        @NonNull String evaluatorId,
        int samplingRate,
        double alertThreshold
    ) {}
}
