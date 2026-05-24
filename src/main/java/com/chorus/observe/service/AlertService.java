package com.chorus.observe.service;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.persistence.AlertEventRepository;
import com.chorus.observe.persistence.AlertRuleRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for alert rule management and event processing.
 */
public class AlertService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final HttpClient httpClient;
    private final com.chorus.observe.notification.NotificationService notificationService;

    public AlertService(@NonNull AlertRuleRepository alertRuleRepository, @NonNull AlertEventRepository alertEventRepository) {
        this(alertRuleRepository, alertEventRepository, null);
    }

    public AlertService(@NonNull AlertRuleRepository alertRuleRepository, @NonNull AlertEventRepository alertEventRepository,
                        com.chorus.observe.notification.NotificationService notificationService) {
        this.alertRuleRepository = Objects.requireNonNull(alertRuleRepository);
        this.alertEventRepository = Objects.requireNonNull(alertEventRepository);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.notificationService = notificationService;
    }

    public @NonNull AlertRule createRule(@NonNull String name, @NonNull String conditionExpr, double threshold, AlertRule.Severity severity, @Nullable String webhookUrl, @Nullable String email, int cooldownSeconds) {
        String ruleId = "alert-" + UUID.randomUUID().toString().substring(0, 8);
        AlertRule rule = new AlertRule(ruleId, name, conditionExpr, threshold, severity, webhookUrl, email, true, cooldownSeconds, Map.of(), Instant.now(), Instant.now());
        alertRuleRepository.save(rule);
        return rule;
    }

    public @NonNull Optional<AlertRule> getRule(@NonNull String ruleId) {
        return alertRuleRepository.findById(ruleId);
    }

    public @NonNull List<AlertRule> listRules() {
        return alertRuleRepository.findAll();
    }

    public @NonNull PagedResult<AlertRule> listRules(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(alertRuleRepository.findAll(size, offset), alertRuleRepository.count(), page, size);
    }

    public @NonNull List<AlertRule> listEnabledRules() {
        return alertRuleRepository.findEnabled();
    }

    public @NonNull PagedResult<AlertRule> listEnabledRules(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(alertRuleRepository.findEnabled(size, offset), alertRuleRepository.countEnabled(), page, size);
    }

    public void updateRule(@NonNull AlertRule rule) {
        alertRuleRepository.save(rule);
    }

    public void deleteRule(@NonNull String ruleId) {
        alertRuleRepository.deleteById(ruleId);
    }

    public @NonNull AlertEvent triggerEvent(@NonNull String ruleId, double value, @NonNull Map<String, Object> metadata) {
        String eventId = "evt-" + UUID.randomUUID().toString().substring(0, 8);
        AlertEvent event = new AlertEvent(eventId, ruleId, Instant.now(), value, null, false, metadata, Instant.now());
        alertEventRepository.save(event);

        Optional<AlertRule> ruleOpt = alertRuleRepository.findById(ruleId);
        if (ruleOpt.isPresent()) {
            AlertRule rule = ruleOpt.get();
            if (rule.webhookUrl() != null && !rule.webhookUrl().isBlank()) {
                sendWebhook(rule.webhookUrl(), event, rule);
            }
            if (notificationService != null) {
                notificationService.dispatchAlert(rule, event);
            }
        }
        return event;
    }

    @Transactional
    public void resolveEvent(@NonNull String eventId) {
        Optional<AlertEvent> opt = alertEventRepository.findById(eventId);
        if (opt.isEmpty()) return;
        AlertEvent event = opt.get();
        alertEventRepository.save(new AlertEvent(
            event.eventId(), event.ruleId(), event.triggeredAt(), event.value(),
            Instant.now(), event.notificationSent(), event.metadata(), event.createdAt()
        ));
    }

    public @NonNull List<AlertEvent> getEventsByRule(@NonNull String ruleId) {
        return alertEventRepository.findByRuleId(ruleId);
    }

    public @NonNull List<AlertEvent> getUnresolvedEvents() {
        return alertEventRepository.findUnresolved();
    }

    public @NonNull PagedResult<AlertEvent> getUnresolvedEvents(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(alertEventRepository.findUnresolved(size, offset), alertEventRepository.countUnresolved(), page, size);
    }

    public @NonNull List<AlertEvent> getRecentEvents(int limit) {
        return alertEventRepository.findRecent(limit);
    }

    public @NonNull PagedResult<AlertEvent> getRecentEvents(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(alertEventRepository.findRecent(size, offset), alertEventRepository.count(), page, size);
    }

    private void sendWebhook(@NonNull String webhookUrl, @NonNull AlertEvent event, @NonNull AlertRule rule) {
        String body = "{\"rule\":\"" + rule.name() + "\",\"value\":" + event.value() + ",\"severity\":\"" + rule.severity() + "\",\"timestamp\":\"" + event.triggeredAt() + "\"}";
        CompletableFuture.runAsync(() -> sendWebhookWithRetry(webhookUrl, body));
    }

    private void sendWebhookWithRetry(@NonNull String webhookUrl, @NonNull String body) {
        int maxRetries = 3;
        long[] delays = {1_000L, 2_000L, 4_000L};

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
                LOG.warn("Webhook attempt {} returned HTTP {} for {}", attempt + 1, response.statusCode(), webhookUrl);
            } catch (Exception e) {
                LOG.warn("Webhook attempt {} failed for {}", attempt + 1, webhookUrl, e);
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(delays[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        LOG.error("Failed to send webhook to {} after {} attempts", webhookUrl, maxRetries + 1);
    }
}
