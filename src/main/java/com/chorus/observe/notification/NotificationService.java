package com.chorus.observe.notification;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.AlertRuleChannel;
import com.chorus.observe.model.NotificationChannel;
import com.chorus.observe.model.NotificationDelivery;
import com.chorus.observe.persistence.AlertEventRepository;
import com.chorus.observe.persistence.AlertRuleChannelRepository;
import com.chorus.observe.persistence.NotificationChannelRepository;
import com.chorus.observe.persistence.NotificationDeliveryRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service for notification channel management and alert dispatch.
 * <p>
 * Delivery tracking: each channel dispatch attempt creates/updates a
 * {@link NotificationDelivery} row. Status transitions:
 * PENDING → SENT (success), PENDING → FAILED (error), FAILED → DLQ (retryCount &ge; 3).
 */
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationChannelRepository notificationChannelRepository;
    private final AlertRuleChannelRepository alertRuleChannelRepository;
    private final AlertEventRepository alertEventRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final Map<NotificationChannel.ChannelType, NotificationDispatcher> dispatchers;

    public NotificationService(@NonNull NotificationChannelRepository notificationChannelRepository,
                               @NonNull AlertRuleChannelRepository alertRuleChannelRepository,
                               @NonNull AlertEventRepository alertEventRepository,
                               @NonNull NotificationDeliveryRepository deliveryRepository,
                               @NonNull List<NotificationDispatcher> dispatcherList) {
        this.notificationChannelRepository = Objects.requireNonNull(notificationChannelRepository);
        this.alertRuleChannelRepository = Objects.requireNonNull(alertRuleChannelRepository);
        this.alertEventRepository = Objects.requireNonNull(alertEventRepository);
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository);
        this.dispatchers = new ConcurrentHashMap<>();
        for (NotificationDispatcher d : dispatcherList) {
            this.dispatchers.put(d.channelType(), d);
        }
    }

    // -----------------------------------------------------------------------
    // Alert dispatch
    // -----------------------------------------------------------------------

    public void dispatchAlert(@NonNull AlertRule rule, @NonNull AlertEvent event) {
        boolean anySuccess = false;
        List<AlertRuleChannel> links = alertRuleChannelRepository.findByRuleId(rule.ruleId());
        for (AlertRuleChannel link : links) {
            anySuccess = dispatchToChannel(rule, event, link) || anySuccess;
        }
        if (anySuccess) {
            AlertEvent updated = new AlertEvent(
                event.eventId(), event.ruleId(), event.triggeredAt(), event.value(),
                event.resolvedAt(), true, event.metadata(), event.createdAt(),
                event.retryCount(), event.nextRetryAt(), event.lastError()
            );
            alertEventRepository.save(updated);
        }
    }

    private boolean dispatchToChannel(@NonNull AlertRule rule, @NonNull AlertEvent event, @NonNull AlertRuleChannel link) {
        var channelOpt = notificationChannelRepository.findById(link.channelId());
        if (channelOpt.isEmpty()) return false;
        NotificationChannel channel = channelOpt.get();
        if (!channel.enabled()) return false;

        NotificationDispatcher dispatcher = dispatchers.get(channel.channelType());
        if (dispatcher == null) {
            LOG.warn("No dispatcher for channel type: {}", channel.channelType());
            return false;
        }

        String deliveryId = "dlv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();

        // Create a PENDING delivery record before attempting dispatch
        NotificationDelivery pending = new NotificationDelivery(
            deliveryId, event.eventId(), channel.channelId(),
            NotificationDelivery.Status.PENDING, event.retryCount(), null, null, now, now);
        deliveryRepository.save(pending);

        try {
            dispatcher.dispatch(channel, rule, event);

            // Update delivery to SENT
            Instant sentAt = Instant.now();
            deliveryRepository.save(new NotificationDelivery(
                deliveryId, event.eventId(), channel.channelId(),
                NotificationDelivery.Status.SENT, event.retryCount(), null, sentAt, now, sentAt));

            // Touch last_used_at on the channel
            notificationChannelRepository.save(new NotificationChannel(
                channel.channelId(), channel.tenantId(), channel.name(), channel.channelType(),
                channel.config(), channel.enabled(), sentAt, channel.createdAt(), sentAt));

            return true;
        } catch (Exception e) {
            Instant failedAt = Instant.now();
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

            // DLQ after 3 retries (mirrors existing AlertEvent retryCount semantics)
            NotificationDelivery.Status finalStatus = event.retryCount() >= 3
                ? NotificationDelivery.Status.DLQ
                : NotificationDelivery.Status.FAILED;

            deliveryRepository.save(new NotificationDelivery(
                deliveryId, event.eventId(), channel.channelId(),
                finalStatus, event.retryCount(), errorMessage, null, now, failedAt));

            LOG.error("Failed to dispatch alert {} to channel {} (status={})",
                event.eventId(), channel.channelId(), finalStatus, e);
            recordFailure(event, e);
            return false;
        }
    }

    private void recordFailure(@NonNull AlertEvent event, @NonNull Exception e) {
        int newRetryCount = event.retryCount() + 1;
        Instant nextRetry = null;
        if (newRetryCount < 3) {
            long backoffSeconds = 30L * (long) Math.pow(4, newRetryCount - 1);
            nextRetry = Instant.now().plusSeconds(backoffSeconds);
        }
        String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        AlertEvent updated = new AlertEvent(
            event.eventId(), event.ruleId(), event.triggeredAt(), event.value(),
            event.resolvedAt(), event.notificationSent(), event.metadata(), event.createdAt(),
            newRetryCount, nextRetry, errorMessage
        );
        alertEventRepository.save(updated);
    }

    // -----------------------------------------------------------------------
    // Channel test
    // -----------------------------------------------------------------------

    /**
     * Dispatches a synthetic alert to {@code channel} to verify connectivity.
     * Creates a temporary placeholder {@link AlertRule} and {@link AlertEvent} to
     * satisfy dispatcher contracts; does NOT persist either record.
     *
     * @param channel the channel to test
     * @return a {@link TestResult} indicating success or the error message
     */
    public @NonNull TestResult testChannel(@NonNull NotificationChannel channel) {
        NotificationDispatcher dispatcher = dispatchers.get(channel.channelType());
        if (dispatcher == null) {
            return new TestResult(false, "No dispatcher registered for channel type: " + channel.channelType());
        }

        Instant now = Instant.now();
        AlertRule syntheticRule = new AlertRule(
            "test-rule-" + channel.channelId(),
            "[Test] " + channel.name(),
            "test",
            0.0,
            AlertRule.Severity.info,
            null,
            null,
            true,
            0,
            Map.of(),
            now,
            now
        );
        AlertEvent syntheticEvent = new AlertEvent(
            "test-event-" + channel.channelId(),
            syntheticRule.ruleId(),
            now,
            0.0,
            null,
            false,
            Map.of("test", true),
            now
        );

        try {
            dispatcher.dispatch(channel, syntheticRule, syntheticEvent);
            return new TestResult(true, null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            LOG.warn("Channel test failed for {}: {}", channel.channelId(), msg);
            return new TestResult(false, msg);
        }
    }

    /** Result returned from {@link #testChannel(NotificationChannel)}. */
    public record TestResult(boolean success, @Nullable String error) {}

    // -----------------------------------------------------------------------
    // Channel CRUD
    // -----------------------------------------------------------------------

    public @NonNull NotificationChannel createChannel(@NonNull String tenantId, @NonNull String name,
                                                       NotificationChannel.ChannelType type,
                                                       @NonNull Map<String, Object> config) {
        String channelId = "ch-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        NotificationChannel channel = new NotificationChannel(channelId, tenantId, name, type, config, true,
            now, now, now);
        notificationChannelRepository.save(channel);
        return channel;
    }

    public @NonNull List<NotificationChannel> listChannels(@NonNull String tenantId) {
        return notificationChannelRepository.findByTenant(tenantId);
    }

    public @NonNull Optional<NotificationChannel> getChannel(@NonNull String channelId) {
        return notificationChannelRepository.findById(channelId);
    }

    public @NonNull NotificationChannel updateChannel(@NonNull NotificationChannel channel) {
        NotificationChannel updated = new NotificationChannel(
            channel.channelId(), channel.tenantId(), channel.name(), channel.channelType(),
            channel.config(), channel.enabled(), channel.lastUsedAt(), channel.createdAt(), Instant.now());
        notificationChannelRepository.save(updated);
        return updated;
    }

    public void deleteChannel(@NonNull String channelId) {
        alertRuleChannelRepository.deleteByChannelId(channelId);
        notificationChannelRepository.deleteById(channelId);
    }

    public @NonNull NotificationChannel toggleChannel(@NonNull String channelId, boolean enabled) {
        NotificationChannel channel = notificationChannelRepository.findById(channelId)
            .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        NotificationChannel toggled = new NotificationChannel(
            channel.channelId(), channel.tenantId(), channel.name(), channel.channelType(),
            channel.config(), enabled, channel.lastUsedAt(), channel.createdAt(), Instant.now());
        notificationChannelRepository.save(toggled);
        return toggled;
    }
}
