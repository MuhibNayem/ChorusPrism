package com.chorus.observe.notification;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.NotificationChannel;
import org.jspecify.annotations.NonNull;

/**
 * Dispatches alert notifications to a specific channel type.
 */
public interface NotificationDispatcher {

    /**
     * @return the channel type this dispatcher handles
     */
    NotificationChannel.ChannelType channelType();

    /**
     * Send a notification for the given alert event.
     *
     * @param channel the configured channel
     * @param rule    the alert rule that triggered
     * @param event   the alert event
     */
    void dispatch(@NonNull NotificationChannel channel, @NonNull AlertRule rule, @NonNull AlertEvent event);
}
