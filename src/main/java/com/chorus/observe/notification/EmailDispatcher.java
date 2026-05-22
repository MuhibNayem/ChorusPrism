package com.chorus.observe.notification;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.NotificationChannel;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Map;
import java.util.Properties;

/**
 * Email notification dispatcher using JavaMail (Jakarta Mail).
 * <p>
 * Config keys in channel config:
 * <ul>
 *   <li>{@code smtpHost} — SMTP server hostname</li>
 *   <li>{@code smtpPort} — SMTP server port (default 587)</li>
 *   <li>{@code username} — SMTP auth username</li>
 *   <li>{@code password} — SMTP auth password</li>
 *   <li>{@code from} — From address</li>
 *   <li>{@code to} — Comma-separated recipient addresses</li>
 *   <li>{@code useTls} — Whether to use STARTTLS (default true)</li>
 * </ul>
 */
public final class EmailDispatcher implements NotificationDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(EmailDispatcher.class);

    @Override
    public NotificationChannel.ChannelType channelType() {
        return NotificationChannel.ChannelType.EMAIL;
    }

    @Override
    public void dispatch(@NonNull NotificationChannel channel, @NonNull AlertRule rule, @NonNull AlertEvent event) {
        Map<String, Object> cfg = channel.config();
        String smtpHost = Objects.toString(cfg.get("smtpHost"), null);
        String to = Objects.toString(cfg.get("to"), null);
        if (smtpHost == null || to == null) {
            LOG.warn("Email channel {} missing smtpHost or to", channel.channelId());
            return;
        }

        int smtpPort = parseInt(cfg.get("smtpPort"), 587);
        boolean useTls = parseBoolean(cfg.get("useTls"), true);
        String from = Objects.toString(cfg.get("from"), "chorus-alerts@localhost");
        String username = Objects.toString(cfg.get("username"), null);
        String password = Objects.toString(cfg.get("password"), null);

        String subject = "Chorus Alert: " + rule.name() + " triggered";
        String body = String.format("""
            Alert Rule: %s
            Severity: %s
            Value: %.2f
            Threshold: %.2f
            Triggered At: %s
            Condition: %s
            """,
            rule.name(),
            rule.severity(),
            event.value(),
            rule.threshold(),
            event.triggeredAt(),
            rule.conditionExpr()
        );

        try {
            sendEmail(smtpHost, smtpPort, useTls, username, password, from, to, subject, body);
            LOG.debug("Email notification sent for alert {}", event.eventId());
        } catch (Exception e) {
            LOG.error("Failed to send email notification for alert {}", event.eventId(), e);
        }
    }

    private void sendEmail(String host, int port, boolean useTls,
                           String username, String password,
                           String from, String to, String subject, String body) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth", username != null ? "true" : "false");
        props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");

        jakarta.mail.Session session = jakarta.mail.Session.getInstance(props, username != null ? new jakarta.mail.Authenticator() {
            @Override
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(username, password);
            }
        } : null);

        jakarta.mail.internet.MimeMessage message = new jakarta.mail.internet.MimeMessage(session);
        message.setFrom(new jakarta.mail.internet.InternetAddress(from));
        for (String recipient : to.split(",")) {
            message.addRecipient(jakarta.mail.Message.RecipientType.TO, new jakarta.mail.internet.InternetAddress(recipient.trim()));
        }
        message.setSubject(subject);
        message.setText(body);

        jakarta.mail.Transport.send(message);
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
