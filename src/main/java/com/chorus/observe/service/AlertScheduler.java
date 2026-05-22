package com.chorus.observe.service;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.persistence.AlertEventRepository;
import com.chorus.observe.persistence.AlertRuleRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Background scheduler that evaluates enabled alert rules against live data.
 * <p>
 * Runs every 60 seconds (configurable via {@code chorus.observe.alert.eval-interval-sec}).
 * For each enabled rule:
 * <ol>
 *   <li>Checks cooldown: skips if the rule triggered within {@code cooldownSeconds}.</li>
 *   <li>Evaluates the condition expression via {@link AlertConditionEvaluator}.</li>
 *   <li>If the value exceeds the threshold, triggers an {@link AlertEvent} and sends webhook.</li>
 * </ol>
 */
public class AlertScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AlertScheduler.class);

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final AlertConditionEvaluator evaluator;
    private final AlertService alertService;

    public AlertScheduler(
            @NonNull AlertRuleRepository alertRuleRepository,
            @NonNull AlertEventRepository alertEventRepository,
            @NonNull AlertConditionEvaluator evaluator,
            @NonNull AlertService alertService) {
        this.alertRuleRepository = Objects.requireNonNull(alertRuleRepository);
        this.alertEventRepository = Objects.requireNonNull(alertEventRepository);
        this.evaluator = Objects.requireNonNull(evaluator);
        this.alertService = Objects.requireNonNull(alertService);
    }

    @Scheduled(fixedRateString = "${chorus.observe.alert.evalIntervalMs:60000}")
    public void evaluateRules() {
        List<AlertRule> rules = alertRuleRepository.findEnabled();
        if (rules.isEmpty()) return;

        LOG.debug("Evaluating {} enabled alert rule(s)", rules.size());
        int triggered = 0;

        for (AlertRule rule : rules) {
            try {
                if (isInCooldown(rule)) {
                    continue;
                }

                Double value = evaluator.evaluate(rule.conditionExpr());
                if (value == null) {
                    continue;
                }

                if (value > rule.threshold()) {
                    LOG.info("Alert rule '{}' triggered: value={} > threshold={}",
                        rule.name(), value, rule.threshold());
                    alertService.triggerEvent(rule.ruleId(), value,
                        Map.of("condition", rule.conditionExpr(), "threshold", rule.threshold()));
                    triggered++;
                }
            } catch (Exception e) {
                LOG.error("Failed to evaluate alert rule {}", rule.ruleId(), e);
            }
        }

        if (triggered > 0) {
            LOG.info("Alert evaluation complete: {} rule(s) triggered", triggered);
        }
    }

    private boolean isInCooldown(@NonNull AlertRule rule) {
        if (rule.cooldownSeconds() <= 0) {
            return false;
        }
        Optional<AlertEvent> recent = alertEventRepository.findMostRecentByRuleId(rule.ruleId());
        if (recent.isEmpty()) {
            return false;
        }
        Instant cutoff = Instant.now().minusSeconds(rule.cooldownSeconds());
        return recent.get().triggeredAt().isAfter(cutoff);
    }
}
