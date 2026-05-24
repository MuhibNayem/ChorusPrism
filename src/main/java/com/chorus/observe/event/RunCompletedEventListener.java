package com.chorus.observe.event;

import com.chorus.observe.model.Evaluator;
import com.chorus.observe.persistence.EvaluatorRepository;
import com.chorus.observe.service.EvaluatorService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Listens for run completion events and automatically triggers configured evaluators.
 */
@Component
public class RunCompletedEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(RunCompletedEventListener.class);

    private final EvaluatorRepository evaluatorRepository;
    private final EvaluatorService evaluatorService;

    public RunCompletedEventListener(@NonNull EvaluatorRepository evaluatorRepository,
                                     @NonNull EvaluatorService evaluatorService) {
        this.evaluatorRepository = Objects.requireNonNull(evaluatorRepository);
        this.evaluatorService = Objects.requireNonNull(evaluatorService);
    }

    @EventListener
    public void onRunCompleted(@NonNull RunCompletedEvent event) {
        LOG.debug("Run completed: {} (status={})", event.runId(), event.status());

        List<Evaluator> evaluators = evaluatorRepository.findByKind("hallucination");
        for (Evaluator evaluator : evaluators) {
            try {
                evaluatorService.evaluateRun(event.runId(), evaluator.evaluatorId());
                LOG.debug("Triggered evaluator {} for run {}", evaluator.evaluatorId(), event.runId());
            } catch (Exception e) {
                LOG.error("Failed to trigger evaluator {} for run {}", evaluator.evaluatorId(), event.runId(), e);
            }
        }
    }
}
