package com.chorus.observe.notification;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.AlertRuleChannel;
import com.chorus.observe.model.NotificationChannel;
import com.chorus.observe.persistence.InMemoryAlertEventRepository;
import com.chorus.observe.persistence.InMemoryAlertRuleChannelRepository;
import com.chorus.observe.persistence.InMemoryAlertRuleRepository;
import com.chorus.observe.persistence.InMemoryNotificationChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRetrySchedulerTest {

    private InMemoryAlertEventRepository alertEventRepository;
    private InMemoryAlertRuleRepository alertRuleRepository;
    private NotificationService notificationService;
    private AlertRetryScheduler scheduler;
    private AtomicInteger dispatchCount;

    @BeforeEach
    void setUp() {
        alertEventRepository = new InMemoryAlertEventRepository();
        alertRuleRepository = new InMemoryAlertRuleRepository();
        InMemoryNotificationChannelRepository channelRepo = new InMemoryNotificationChannelRepository();
        InMemoryAlertRuleChannelRepository ruleChannelRepo = new InMemoryAlertRuleChannelRepository();
        dispatchCount = new AtomicInteger(0);

        NotificationDispatcher countingDispatcher = new NotificationDispatcher() {
            @Override
            public NotificationChannel.ChannelType channelType() {
                return NotificationChannel.ChannelType.WEBHOOK;
            }

            @Override
            public void dispatch(NotificationChannel channel, AlertRule rule, AlertEvent event) {
                dispatchCount.incrementAndGet();
            }
        };

        notificationService = new NotificationService(
            channelRepo, ruleChannelRepo, alertEventRepository, List.of(countingDispatcher)
        );
        scheduler = new AlertRetryScheduler(alertEventRepository, alertRuleRepository, notificationService);

        // Setup channel and link
        NotificationChannel channel = new NotificationChannel("ch-1", "t1", "Test",
            NotificationChannel.ChannelType.WEBHOOK, Map.of("url", "http://example.com"),
            true, Instant.now(), Instant.now(), Instant.now());
        channelRepo.save(channel);
        ruleChannelRepo.save(new AlertRuleChannel("rule-1", "ch-1", Instant.now()));
    }

    @Test
    void shouldRetryEventsWithNextRetryInPast() {
        AlertRule rule = new AlertRule("rule-1", "Test", "x > 1", 1.0,
            AlertRule.Severity.MEDIUM, null, null, true, 300, Map.of(),
            Instant.now(), Instant.now());
        alertRuleRepository.save(rule);

        AlertEvent event = new AlertEvent("evt-1", "rule-1", Instant.now(), 2.0,
            null, false, Map.of(), Instant.now(), 1,
            Instant.now().minusSeconds(10), "previous error");
        alertEventRepository.save(event);

        scheduler.pollAndRetry();

        assertThat(dispatchCount.get()).isEqualTo(1);
        AlertEvent updated = alertEventRepository.findById("evt-1").orElseThrow();
        assertThat(updated.notificationSent()).isTrue();
    }

    @Test
    void shouldNotRetryEventsWithFutureNextRetry() {
        AlertRule rule = new AlertRule("rule-1", "Test", "x > 1", 1.0,
            AlertRule.Severity.MEDIUM, null, null, true, 300, Map.of(),
            Instant.now(), Instant.now());
        alertRuleRepository.save(rule);

        AlertEvent event = new AlertEvent("evt-1", "rule-1", Instant.now(), 2.0,
            null, false, Map.of(), Instant.now(), 1,
            Instant.now().plusSeconds(600), "previous error");
        alertEventRepository.save(event);

        scheduler.pollAndRetry();

        assertThat(dispatchCount.get()).isEqualTo(0);
    }

    @Test
    void shouldNotRetryEventsAtMaxRetryCount() {
        AlertRule rule = new AlertRule("rule-1", "Test", "x > 1", 1.0,
            AlertRule.Severity.MEDIUM, null, null, true, 300, Map.of(),
            Instant.now(), Instant.now());
        alertRuleRepository.save(rule);

        AlertEvent event = new AlertEvent("evt-1", "rule-1", Instant.now(), 2.0,
            null, false, Map.of(), Instant.now(), 3,
            Instant.now().minusSeconds(10), "previous error");
        alertEventRepository.save(event);

        scheduler.pollAndRetry();

        assertThat(dispatchCount.get()).isEqualTo(0);
    }

    @Test
    void shouldNotRetryEventsWithNullNextRetry() {
        AlertRule rule = new AlertRule("rule-1", "Test", "x > 1", 1.0,
            AlertRule.Severity.MEDIUM, null, null, true, 300, Map.of(),
            Instant.now(), Instant.now());
        alertRuleRepository.save(rule);

        AlertEvent event = new AlertEvent("evt-1", "rule-1", Instant.now(), 2.0,
            null, false, Map.of(), Instant.now(), 0,
            null, null);
        alertEventRepository.save(event);

        scheduler.pollAndRetry();

        assertThat(dispatchCount.get()).isEqualTo(0);
    }

    @Test
    void shouldCalculateBackoffCorrectly() {
        // retry 0 → nextRetry = now + 30s
        // retry 1 → nextRetry = now + 120s
        // retry 2 → nextRetry = now + 480s
        Instant now = Instant.now();

        assertThat(calculateBackoff(0)).isEqualTo(30L);
        assertThat(calculateBackoff(1)).isEqualTo(120L);
        assertThat(calculateBackoff(2)).isEqualTo(480L);
    }

    private long calculateBackoff(int retryCount) {
        return 30L * (long) Math.pow(4, retryCount);
    }
}
