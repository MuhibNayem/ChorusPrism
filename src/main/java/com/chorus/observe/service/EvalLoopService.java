package com.chorus.observe.service;

import com.chorus.observe.model.EvalLoop;
import com.chorus.observe.persistence.EvalLoopRepository;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for continuous evaluation loops configuration.
 */
public class EvalLoopService {

    private final EvalLoopRepository evalLoopRepository;

    public EvalLoopService(@NonNull EvalLoopRepository evalLoopRepository) {
        this.evalLoopRepository = Objects.requireNonNull(evalLoopRepository);
    }

    public @NonNull List<EvalLoop> listLoops() {
        return evalLoopRepository.findAll();
    }

    public @NonNull Optional<EvalLoop> getLoop(@NonNull String loopId) {
        return evalLoopRepository.findById(loopId);
    }

    public @NonNull List<EvalLoop> getLoopsByAgent(@NonNull String agentId) {
        return evalLoopRepository.findByAgentId(agentId);
    }

    public @NonNull EvalLoop createLoop(
            @NonNull String agentId,
            @NonNull String evaluatorId,
            int samplingRate,
            double alertThreshold) {
        String loopId = "lp-" + UUID.randomUUID().toString().substring(0, 8);
        EvalLoop loop = new EvalLoop(
            loopId, agentId, evaluatorId, samplingRate, alertThreshold, "ACTIVE", Instant.now(), null
        );
        evalLoopRepository.save(loop);
        return loop;
    }

    public @NonNull EvalLoop toggleLoop(@NonNull String loopId, @NonNull String status) {
        EvalLoop loop = evalLoopRepository.findById(loopId)
            .orElseThrow(() -> new IllegalArgumentException("Loop not found: " + loopId));
        EvalLoop updated = new EvalLoop(
            loop.loopId(), loop.agentId(), loop.evaluatorId(),
            loop.samplingRate(), loop.alertThreshold(), status,
            loop.createdAt(), loop.lastRunAt()
        );
        evalLoopRepository.save(updated);
        return updated;
    }

    public void updateLastRun(@NonNull String loopId) {
        evalLoopRepository.findById(loopId).ifPresent(loop -> {
            EvalLoop updated = new EvalLoop(
                loop.loopId(), loop.agentId(), loop.evaluatorId(),
                loop.samplingRate(), loop.alertThreshold(), loop.status(),
                loop.createdAt(), Instant.now()
            );
            evalLoopRepository.save(updated);
        });
    }

    public void deleteLoop(@NonNull String loopId) {
        evalLoopRepository.deleteById(loopId);
    }
}
