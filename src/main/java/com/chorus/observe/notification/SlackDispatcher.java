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

public final class SlackDispatcher implements NotificationDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(SlackDispatcher.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public SlackDispatcher(@NonNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public NotificationChannel.ChannelType channelType() {
        return NotificationChannel.ChannelType.SLACK;
    }

    @Override
    public void dispatch(@NonNull NotificationChannel channel, @NonNull AlertRule rule, @NonNull AlertEvent event) {
        Object webhookUrlObj = channel.config().get("webhookUrl");
        if (webhookUrlObj == null) {
            LOG.warn("Slack channel {} has no webhookUrl configured", channel.channelId());
            return;
        }
        String webhookUrl = webhookUrlObj.toString();
        String severity = rule.severity() != null ? rule.severity().name() : "UNKNOWN";
        String emoji = switch (severity) {
            case "CRITICAL" -> ":rotating_light:";
            case "HIGH" -> ":warning:";
            case "MEDIUM" -> ":exclamation:";
            default -> ":information_source:";
        };

        String text = String.format("%s *Chorus Alert* | *%s* | Rule: `%s` | Value: `%.2f` | Time: %s",
            emoji, severity, rule.name(), event.value(), event.triggeredAt());

        try {
            String body = mapper.writeValueAsString(Map.of("text", text));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.debug("Slack notification sent for alert {}", event.eventId());
            } else {
                LOG.warn("Slack notification failed: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            LOG.error("Failed to send Slack notification for alert {}", event.eventId(), e);
        }
    }
}
