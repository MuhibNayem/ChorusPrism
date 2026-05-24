package com.chorus.observe.notification;

import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    private InMemoryNotificationChannelRepository channelRepository;
    private InMemoryAlertRuleChannelRepository ruleChannelRepository;
    private InMemoryAlertEventRepository alertEventRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        channelRepository = new InMemoryNotificationChannelRepository();
        ruleChannelRepository = new InMemoryAlertRuleChannelRepository();
        alertEventRepository = new InMemoryAlertEventRepository();
    }

    @Test
    void shouldTrackRetryOnDispatcherFailure() {
        // Given: a dispatcher that always fails
        NotificationDispatcher failingDispatcher = new NotificationDispatcher() {
            @Override
            public NotificationChannel.ChannelType channelType() {
                return NotificationChannel.ChannelType.SLACK;
            }

            @Override
            public void dispatch(NotificationChannel channel, AlertRule rule, AlertEvent event) {
                throw new RuntimeException("Network error");
            }
        };

        notificationService = new NotificationService(
            channelRepository, ruleChannelRepository, alertEventRepository, List.of(failingDispatcher)
        );

        AlertRule rule = new AlertRule("rule-1", "Test", "x > 1", 1.0,
            AlertRule.Severity.HIGH, null, null, true, 300, Map.of(),
            Instant.now(), Instant.now());
        AlertEvent event = new AlertEvent("evt-1", "rule-1", Instant.now(), 2.0,
            null, false, Map.of(), Instant.now());
        alertEventRepository.save(event);

        NotificationChannel channel = new NotificationChannel("ch-1", "tenant-1", "Test",
            NotificationChannel.ChannelType.SLACK, Map.of("webhookUrl", "http://example.com"),
            true, Instant.now(), Instant.now(), Instant.now());
        channelRepository.save(channel);
        ruleChannelRepository.save(new AlertRuleChannel("rule-1", "ch-1", Instant.now()));

        // When
        notificationService.dispatchAlert(rule, event);

        // Then: retry state should be updated
        AlertEvent updated = alertEventRepository.findById("evt-1").orElseThrow();
        assertThat(updated.retryCount()).isEqualTo(1);
        assertThat(updated.nextRetryAt()).isNotNull();
        assertThat(updated.lastError()).isEqualTo("Network error");
    }

    @Test
    void shouldSetNotificationSentOnSuccess() {
        NotificationDispatcher successDispatcher = new NotificationDispatcher() {
            @Override
            public NotificationChannel.ChannelType channelType() {
                return NotificationChannel.ChannelType.WEBHOOK;
            }

            @Override
            public void dispatch(NotificationChannel channel, AlertRule rule, AlertEvent event) {
                // success, no-op
            }
        };

        notificationService = new NotificationService(
            channelRepository, ruleChannelRepository, alertEventRepository, List.of(successDispatcher)
        );

        AlertRule rule = new AlertRule("rule-1", "Test", "x > 1", 1.0,
            AlertRule.Severity.MEDIUM, null, null, true, 300, Map.of(),
            Instant.now(), Instant.now());
        AlertEvent event = new AlertEvent("evt-1", "rule-1", Instant.now(), 2.0,
            null, false, Map.of(), Instant.now());
        alertEventRepository.save(event);

        NotificationChannel channel = new NotificationChannel("ch-1", "tenant-1", "Test",
            NotificationChannel.ChannelType.WEBHOOK, Map.of("url", "http://example.com"),
            true, Instant.now(), Instant.now(), Instant.now());
        channelRepository.save(channel);
        ruleChannelRepository.save(new AlertRuleChannel("rule-1", "ch-1", Instant.now()));

        notificationService.dispatchAlert(rule, event);

        AlertEvent updated = alertEventRepository.findById("evt-1").orElseThrow();
        assertThat(updated.notificationSent()).isTrue();
        assertThat(updated.retryCount()).isEqualTo(0);
    }

    @Test
    void shouldNotThrowWhenNoChannelsLinked() {
        notificationService = new NotificationService(
            channelRepository, ruleChannelRepository, alertEventRepository, List.of()
        );

        AlertRule rule = new AlertRule("rule-1", "Test", "x > 1", 1.0,
            AlertRule.Severity.LOW, null, null, true, 300, Map.of(),
            Instant.now(), Instant.now());
        AlertEvent event = new AlertEvent("evt-1", "rule-1", Instant.now(), 2.0,
            null, false, Map.of(), Instant.now());
        alertEventRepository.save(event);

        notificationService.dispatchAlert(rule, event);

        AlertEvent updated = alertEventRepository.findById("evt-1").orElseThrow();
        assertThat(updated.retryCount()).isEqualTo(0);
    }
}
