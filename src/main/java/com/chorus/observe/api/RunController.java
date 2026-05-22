package com.chorus.observe.api;

import com.chorus.observe.model.Run;
import com.chorus.observe.model.Span;
import com.chorus.observe.persistence.RunRepository.RunQuery;
import com.chorus.observe.service.RunService;
import com.chorus.observe.service.SpanStreamService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for runs.
 */
@RestController
@RequestMapping("/api/v1/runs")
public class RunController {

    private final RunService runService;
    private final SpanStreamService spanStreamService;

    public RunController(@NonNull RunService runService, @NonNull SpanStreamService spanStreamService) {
        this.runService = Objects.requireNonNull(runService);
        this.spanStreamService = Objects.requireNonNull(spanStreamService);
    }

    @GetMapping
    public ResponseEntity<RunListResponse> listRuns(
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Run.Status status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "start_time") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "0") int page) {

        int offset = page * size;
        RunQuery query = new RunQuery(framework, agentId, model, status, from, to, null, null, search, sortBy, sortOrder, size, offset);
        List<Run> runs = runService.listRuns(query);
        long total = runService.countRuns(query);

        List<String> runIds = runs.stream().map(Run::runId).toList();
        Map<String, RunService.RunEvalSummary> evalSummaryByRunId = runService.getEvalSummariesForRuns(runIds).stream()
            .collect(java.util.stream.Collectors.toMap(RunService.RunEvalSummary::runId, s -> s));

        List<RunWithEvalSummary> runsWithSummary = runs.stream()
            .map(run -> new RunWithEvalSummary(run, evalSummaryByRunId.get(run.runId())))
            .toList();

        return ResponseEntity.ok(new RunListResponse(runsWithSummary, total, page, size));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<Run> getRun(@PathVariable @NonNull String runId) {
        return runService.getRun(runId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable @NonNull String runId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout
        spanStreamService.subscribe(runId, emitter);
        emitter.onCompletion(() -> spanStreamService.unsubscribe(runId, emitter));
        emitter.onTimeout(() -> spanStreamService.unsubscribe(runId, emitter));
        return emitter;
    }

    @GetMapping("/{runId}/eval-summary")
    public ResponseEntity<RunService.RunEvalSummary> getRunEvalSummary(@PathVariable @NonNull String runId) {
        RunService.RunEvalSummary summary = runService.getEvalSummaryForRun(runId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(summary);
    }

    public record RunWithEvalSummary(@NonNull Run run, RunService.@Nullable RunEvalSummary evalSummary) {}

    public record RunListResponse(
        @NonNull List<RunWithEvalSummary> runs,
        long total,
        int page,
        int size
    ) {}
}
