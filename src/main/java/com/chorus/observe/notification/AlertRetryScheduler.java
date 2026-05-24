package com.chorus.observe.notification;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.persistence.AlertEventRepository;
import com.chorus.observe.persistence.AlertRuleRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class AlertRetryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AlertRetryScheduler.class);

    private final AlertEventRepository alertEventRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final NotificationService notificationService;

    public AlertRetryScheduler(@NonNull AlertEventRepository alertEventRepository,
                               @NonNull AlertRuleRepository alertRuleRepository,
                               @NonNull NotificationService notificationService) {
        this.alertEventRepository = Objects.requireNonNull(alertEventRepository);
        this.alertRuleRepository = Objects.requireNonNull(alertRuleRepository);
        this.notificationService = Objects.requireNonNull(notificationService);
    }

    @Scheduled(fixedDelay = 60_000)
    public void pollAndRetry() {
        List<AlertEvent> retryable = alertEventRepository.findRetryable();
        if (retryable.isEmpty()) return;

        LOG.debug("Retrying {} alert event(s)", retryable.size());
        for (AlertEvent event : retryable) {
            alertRuleRepository.findById(event.ruleId()).ifPresent(rule -> {
                try {
                    notificationService.dispatchAlert(rule, event);
                } catch (Exception e) {
                    LOG.error("Retry dispatch failed for alert {}", event.eventId(), e);
                }
            });
        }
    }
}
