package com.chorus.observe.notification;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.AlertRuleChannel;
import com.chorus.observe.model.NotificationChannel;
import com.chorus.observe.persistence.AlertRuleChannelRepository;
import com.chorus.observe.persistence.NotificationChannelRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationChannelRepository notificationChannelRepository;
    private final AlertRuleChannelRepository alertRuleChannelRepository;
    private final Map<NotificationChannel.ChannelType, NotificationDispatcher> dispatchers;

    public NotificationService(@NonNull NotificationChannelRepository notificationChannelRepository,
                               @NonNull AlertRuleChannelRepository alertRuleChannelRepository,
                               @NonNull List<NotificationDispatcher> dispatcherList) {
        this.notificationChannelRepository = Objects.requireNonNull(notificationChannelRepository);
        this.alertRuleChannelRepository = Objects.requireNonNull(alertRuleChannelRepository);
        this.dispatchers = new ConcurrentHashMap<>();
        for (NotificationDispatcher d : dispatcherList) {
            this.dispatchers.put(d.channelType(), d);
        }
    }

    public void dispatchAlert(@NonNull AlertRule rule, @NonNull AlertEvent event) {
        List<AlertRuleChannel> links = alertRuleChannelRepository.findByRuleId(rule.ruleId());
        for (AlertRuleChannel link : links) {
            notificationChannelRepository.findById(link.channelId()).ifPresent(channel -> {
                if (!channel.enabled()) return;
                NotificationDispatcher dispatcher = dispatchers.get(channel.channelType());
                if (dispatcher == null) {
                    LOG.warn("No dispatcher for channel type: {}", channel.channelType());
                    return;
                }
                try {
                    dispatcher.dispatch(channel, rule, event);
                    notificationChannelRepository.save(new NotificationChannel(
                        channel.channelId(), channel.tenantId(), channel.name(), channel.channelType(),
                        channel.config(), channel.enabled(), Instant.now(), channel.createdAt(), channel.updatedAt()));
                } catch (Exception e) {
                    LOG.error("Failed to dispatch alert {} to channel {}", event.eventId(), channel.channelId(), e);
                }
            });
        }
    }

    public @NonNull NotificationChannel createChannel(@NonNull String tenantId, @NonNull String name,
                                                       NotificationChannel.ChannelType type,
                                                       @NonNull Map<String, Object> config) {
        String channelId = "ch-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        NotificationChannel channel = new NotificationChannel(channelId, tenantId, name, type, config, true,
            Instant.now(), Instant.now(), Instant.now());
        notificationChannelRepository.save(channel);
        return channel;
    }
}
