package com.chorus.observe.notification;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class PagerDutyDispatcher implements NotificationDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(PagerDutyDispatcher.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PagerDutyDispatcher(@NonNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public NotificationChannel.ChannelType channelType() {
        return NotificationChannel.ChannelType.PAGERDUTY;
    }

    @Override
    public void dispatch(@NonNull NotificationChannel channel, @NonNull AlertRule rule, @NonNull AlertEvent event) {
        Object routingKeyObj = channel.config().get("routingKey");
        if (routingKeyObj == null) {
            LOG.warn("PagerDuty channel {} has no routingKey configured", channel.channelId());
            return;
        }
        String routingKey = routingKeyObj.toString();
        String severity = mapSeverity(rule.severity());

        try {
            Map<String, Object> payload = Map.of(
                "routing_key", routingKey,
                "event_action", "trigger",
                "dedup_key", event.eventId(),
                "payload", Map.of(
                    "summary", "Chorus Alert: " + rule.name() + " = " + String.format("%.2f", event.value()),
                    "severity", severity,
                    "source", "chorus-observe",
                    "custom_details", Map.of(
                        "ruleId", rule.ruleId(),
                        "condition", rule.conditionExpr(),
                        "threshold", rule.threshold(),
                        "value", event.value()
                    )
                )
            );
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://events.pagerduty.com/v2/enqueue"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.debug("PagerDuty notification sent for alert {}", event.eventId());
            } else {
                LOG.warn("PagerDuty notification failed: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            LOG.error("Failed to send PagerDuty notification for alert {}", event.eventId(), e);
        }
    }

    private String mapSeverity(AlertRule.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "critical";
            case HIGH -> "error";
            case MEDIUM -> "warning";
            case LOW -> "info";
        };
    }
}
