package com.chorus.observe.notification;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TeamsDispatcher dispatcher = new TeamsDispatcher(mapper);

    @Test
    void shouldReturnTeamsChannelType() {
        assertThat(dispatcher.channelType()).isEqualTo(NotificationChannel.ChannelType.TEAMS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildAdaptiveCardPayload() {
        AlertRule rule = new AlertRule(
            "rule-1", "High Latency", "latency > 1000",
            1000.0, AlertRule.Severity.error, null, null,
            true, 300, Map.of(), Instant.now(), Instant.now()
        );
        AlertEvent event = new AlertEvent(
            "evt-1", "rule-1", Instant.parse("2026-05-23T10:00:00Z"),
            1500.0, null, false, Map.of(), Instant.now()
        );

        Map<String, Object> payload = dispatcher.buildPayload(rule, event);

        assertThat(payload.get("@type")).isEqualTo("MessageCard");
        assertThat(payload.get("@context")).isEqualTo("https://schema.org/extensions");
        assertThat(payload.get("themeColor")).isEqualTo("FF0000");
        assertThat(payload.get("summary")).isEqualTo("Chorus Alert: High Latency");

        List<Map<String, Object>> sections = (List<Map<String, Object>>) payload.get("sections");
        assertThat(sections).hasSize(1);
        Map<String, Object> section = sections.get(0);
        assertThat(section.get("activityTitle")).isEqualTo("Chorus Alert");
        assertThat(section.get("activitySubtitle")).isEqualTo("error | High Latency");

        List<Map<String, Object>> facts = (List<Map<String, Object>>) section.get("facts");
        assertThat(facts).hasSize(6);
        assertThat(facts.get(0)).containsEntry("name", "Rule").containsEntry("value", "High Latency");
        assertThat(facts.get(1)).containsEntry("name", "Severity").containsEntry("value", "error");
        assertThat(facts.get(2)).containsEntry("name", "Value").containsEntry("value", "1500.00");
        assertThat(facts.get(3)).containsEntry("name", "Threshold").containsEntry("value", "1000.00");
        assertThat(facts.get(4)).containsEntry("name", "Time").containsEntry("value", "2026-05-23T10:00:00Z");
        assertThat(facts.get(5)).containsEntry("name", "Event ID").containsEntry("value", "evt-1");
    }

    @Test
    void criticalSeverityShouldUseRedTheme() {
        AlertRule rule = new AlertRule(
            "rule-2", "System Down", "error_rate > 0.5",
            0.5, AlertRule.Severity.error, null, null,
            true, 300, Map.of(), Instant.now(), Instant.now()
        );
        AlertEvent event = new AlertEvent(
            "evt-2", "rule-2", Instant.now(), 0.8, null, false, Map.of(), Instant.now()
        );

        Map<String, Object> payload = dispatcher.buildPayload(rule, event);
        assertThat(payload.get("themeColor")).isEqualTo("FF0000");
    }

    @Test
    void lowSeverityShouldUseGreenTheme() {
        AlertRule rule = new AlertRule(
            "rule-3", "Info", "count > 0",
            0.0, AlertRule.Severity.info, null, null,
            true, 300, Map.of(), Instant.now(), Instant.now()
        );
        AlertEvent event = new AlertEvent(
            "evt-3", "rule-3", Instant.now(), 1.0, null, false, Map.of(), Instant.now()
        );

        Map<String, Object> payload = dispatcher.buildPayload(rule, event);
        assertThat(payload.get("themeColor")).isEqualTo("008000");
    }

    @Test
    void dispatchWithNullWebhookUrlShouldNotThrow() {
        NotificationChannel channel = new NotificationChannel(
            "ch-1", "tenant-1", "Test Teams",
            NotificationChannel.ChannelType.TEAMS, Map.of(),
            true, Instant.now(), Instant.now(), Instant.now()
        );
        AlertRule rule = new AlertRule(
            "rule-1", "Test", "x > 1", 1.0, AlertRule.Severity.warning,
            null, null, true, 300, Map.of(), Instant.now(), Instant.now()
        );
        AlertEvent event = new AlertEvent(
            "evt-1", "rule-1", Instant.now(), 2.0, null, false, Map.of(), Instant.now()
        );

        // Should complete without throwing even though webhookUrl is missing
        dispatcher.dispatch(channel, rule, event);
    }
}
