package com.chorus.observe.service;

import com.chorus.observe.model.Breakpoint;
import com.chorus.observe.model.Checkpoint;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.ReplayRun;
import com.chorus.observe.persistence.BreakpointRepository;
import com.chorus.observe.persistence.CheckpointRepository;
import com.chorus.observe.persistence.ReplayRunRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for time-travel debugging: checkpoints, replay, and breakpoints.
 */
public class TimeTravelService {

    private final CheckpointRepository checkpointRepository;
    private final ReplayRunRepository replayRunRepository;
    private final BreakpointRepository breakpointRepository;

    public TimeTravelService(
            @NonNull CheckpointRepository checkpointRepository,
            @NonNull ReplayRunRepository replayRunRepository,
            @NonNull BreakpointRepository breakpointRepository) {
        this.checkpointRepository = Objects.requireNonNull(checkpointRepository);
        this.replayRunRepository = Objects.requireNonNull(replayRunRepository);
        this.breakpointRepository = Objects.requireNonNull(breakpointRepository);
    }

    public @NonNull Checkpoint saveCheckpoint(@NonNull String runId, int sequence, @NonNull Map<String, Object> stateSnapshot, @NonNull List<String> nextNodes) {
        String checkpointId = "cp-" + UUID.randomUUID().toString().substring(0, 8);
        Checkpoint checkpoint = new Checkpoint(checkpointId, runId, sequence, stateSnapshot, nextNodes, Map.of(), Instant.now());
        checkpointRepository.save(checkpoint);
        return checkpoint;
    }

    public @NonNull List<Checkpoint> getCheckpoints(@NonNull String runId) {
        return checkpointRepository.findByRunId(runId);
    }

    public @NonNull PagedResult<Checkpoint> getCheckpoints(@NonNull String runId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(checkpointRepository.findByRunId(runId, size, offset), checkpointRepository.countByRunId(runId), page, size);
    }

    public @NonNull Optional<Checkpoint> getCheckpoint(@NonNull String checkpointId) {
        return checkpointRepository.findById(checkpointId);
    }

    public @NonNull Optional<Checkpoint> getLatestCheckpoint(@NonNull String runId) {
        return checkpointRepository.findLatestByRunId(runId);
    }

    public @NonNull ReplayRun createReplay(@NonNull String originalRunId, @Nullable String fromCheckpointId, @NonNull Map<String, Object> stateOverrides) {
        String replayRunId = "replay-" + UUID.randomUUID().toString().substring(0, 8);
        ReplayRun replay = new ReplayRun(replayRunId, originalRunId, fromCheckpointId, stateOverrides, ReplayRun.Status.PENDING, null, null, Instant.now());
        replayRunRepository.save(replay);
        return replay;
    }

    public @NonNull Optional<ReplayRun> getReplayRun(@NonNull String replayRunId) {
        return replayRunRepository.findById(replayRunId);
    }

    public @NonNull List<ReplayRun> getReplayRuns(@NonNull String originalRunId) {
        return replayRunRepository.findByOriginalRunId(originalRunId);
    }

    public @NonNull PagedResult<ReplayRun> getReplayRuns(@NonNull String originalRunId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(replayRunRepository.findByOriginalRunId(originalRunId, size, offset), replayRunRepository.countByOriginalRunId(originalRunId), page, size);
    }

    public @NonNull Breakpoint createBreakpoint(@NonNull String runId, @Nullable String beforeNode, @Nullable String beforeTool) {
        String breakpointId = "bp-" + UUID.randomUUID().toString().substring(0, 8);
        Breakpoint breakpoint = new Breakpoint(breakpointId, runId, beforeNode, beforeTool, Breakpoint.Status.ACTIVE, Instant.now());
        breakpointRepository.save(breakpoint);
        return breakpoint;
    }

    public @NonNull List<Breakpoint> getBreakpoints(@NonNull String runId) {
        return breakpointRepository.findByRunId(runId);
    }

    public @NonNull PagedResult<Breakpoint> getBreakpoints(@NonNull String runId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(breakpointRepository.findByRunId(runId, size, offset), breakpointRepository.countByRunId(runId), page, size);
    }

    public @NonNull List<Breakpoint> getActiveBreakpoints(@NonNull String runId) {
        return breakpointRepository.findActiveByRunId(runId);
    }

    public @NonNull PagedResult<Breakpoint> getActiveBreakpoints(@NonNull String runId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(breakpointRepository.findActiveByRunId(runId, size, offset), breakpointRepository.countActiveByRunId(runId), page, size);
    }

    @Transactional
    public void resolveBreakpoint(@NonNull String breakpointId) {
        Optional<Breakpoint> opt = breakpointRepository.findById(breakpointId);
        if (opt.isEmpty()) return;
        Breakpoint bp = opt.get();
        breakpointRepository.save(new Breakpoint(bp.breakpointId(), bp.runId(), bp.beforeNode(), bp.beforeTool(), Breakpoint.Status.RESOLVED, bp.createdAt()));
    }

    public void deleteBreakpoint(@NonNull String breakpointId) {
        breakpointRepository.deleteById(breakpointId);
    }
}
