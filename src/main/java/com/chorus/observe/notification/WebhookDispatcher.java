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

public final class WebhookDispatcher implements NotificationDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public WebhookDispatcher(@NonNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public NotificationChannel.ChannelType channelType() {
        return NotificationChannel.ChannelType.WEBHOOK;
    }

    @Override
    public void dispatch(@NonNull NotificationChannel channel, @NonNull AlertRule rule, @NonNull AlertEvent event) {
        Object urlObj = channel.config().get("url");
        if (urlObj == null) {
            LOG.warn("Webhook channel {} has no URL configured", channel.channelId());
            return;
        }
        String webhookUrl = urlObj.toString();

        try {
            UrlValidator.validate(webhookUrl);
        } catch (IllegalArgumentException e) {
            LOG.error("Blocked SSRF attempt via webhook channel {}: {}", channel.channelId(), e.getMessage());
            return;
        }

        String severity = rule.severity() != null ? rule.severity().name() : "UNKNOWN";

        try {
            Map<String, Object> payload = Map.of(
                "ruleId", rule.ruleId(),
                "ruleName", rule.name(),
                "severity", severity,
                "value", event.value(),
                "threshold", rule.threshold(),
                "timestamp", event.triggeredAt().toString(),
                "eventId", event.eventId(),
                "channelId", channel.channelId()
            );
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.debug("Webhook notification sent for alert {}", event.eventId());
            } else {
                LOG.warn("Webhook notification failed: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            LOG.error("Failed to send webhook notification for alert {}", event.eventId(), e);
        }
    }
}
