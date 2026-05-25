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
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TeamsDispatcher implements NotificationDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(TeamsDispatcher.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public TeamsDispatcher(@NonNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public NotificationChannel.ChannelType channelType() {
        return NotificationChannel.ChannelType.TEAMS;
    }

    @Override
    public void dispatch(@NonNull NotificationChannel channel, @NonNull AlertRule rule, @NonNull AlertEvent event) {
        Object webhookUrlObj = channel.config().get("webhookUrl");
        if (webhookUrlObj == null) {
            LOG.warn("Teams channel {} has no webhookUrl configured", channel.channelId());
            return;
        }
        String webhookUrl = webhookUrlObj.toString();
        Map<String, Object> card = buildPayload(rule, event);

        try {
            String body = mapper.writeValueAsString(card);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.debug("Teams notification sent for alert {}", event.eventId());
            } else {
                LOG.warn("Teams notification failed: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            LOG.error("Failed to send Teams notification for alert {}", event.eventId(), e);
        }
    }

    Map<String, Object> buildPayload(@NonNull AlertRule rule, @NonNull AlertEvent event) {
        String severity = rule.severity() != null ? rule.severity().name() : "UNKNOWN";
        String themeColor = switch (severity) {
            case "error" -> "FF0000";
            case "warning" -> "FFD700";
            case "info" -> "008000";
            default -> "008000";
        };

        return Map.of(
            "@type", "MessageCard",
            "@context", "https://schema.org/extensions",
            "themeColor", themeColor,
            "summary", "Chorus Alert: " + rule.name(),
            "sections", List.of(Map.of(
                "activityTitle", "Chorus Alert",
                "activitySubtitle", severity + " | " + rule.name(),
                "facts", List.of(
                    Map.of("name", "Rule", "value", rule.name()),
                    Map.of("name", "Severity", "value", severity),
                    Map.of("name", "Value", "value", String.format("%.2f", event.value())),
                    Map.of("name", "Threshold", "value", String.format("%.2f", rule.threshold())),
                    Map.of("name", "Time", "value", event.triggeredAt().toString()),
                    Map.of("name", "Event ID", "value", event.eventId())
                ),
                "markdown", true
            ))
        );
    }
}
