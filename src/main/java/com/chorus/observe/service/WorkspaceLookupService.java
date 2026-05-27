package com.chorus.observe.service;

import com.chorus.observe.config.ChorusObserveProperties;
import com.chorus.observe.model.User;
import com.chorus.observe.persistence.SmtpConfigRepository;
import com.chorus.observe.persistence.TenantRepository;
import com.chorus.observe.persistence.UserRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the "forgot workspace ID" flow.
 * Looks up workspaces by email and sends the IDs to that address — the
 * workspace ID is never returned in the HTTP response (prevents enumeration).
 * Rate-limited to 3 requests per 15 minutes per email address.
 */
public class WorkspaceLookupService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceLookupService.class);
    private static final int MAX_REQUESTS = 3;
    private static final long WINDOW_SECONDS = 15 * 60;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ChorusObserveProperties properties;
    private final SmtpConfigRepository smtpConfigRepository;

    private final ConcurrentHashMap<String, long[]> rateLimitBuckets = new ConcurrentHashMap<>();

    public WorkspaceLookupService(@NonNull UserRepository userRepository,
                                   @NonNull TenantRepository tenantRepository,
                                   @NonNull ChorusObserveProperties properties,
                                   @NonNull SmtpConfigRepository smtpConfigRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.properties = properties;
        this.smtpConfigRepository = smtpConfigRepository;
    }

    /**
     * Sends workspace ID(s) to the given email if any accounts exist.
     * Always returns without revealing whether the email was found.
     *
     * @return false if rate-limited, true otherwise
     */
    public boolean sendWorkspaceId(@NonNull String email) {
        String key = email.toLowerCase().trim();
        if (!checkRateLimit(key)) {
            return false;
        }

        List<User> users = userRepository.findByEmailGlobal(key);
        if (users.isEmpty()) {
            LOG.debug("Workspace lookup: no accounts found for email (not disclosed to caller)");
            return true;
        }

        StringBuilder body = new StringBuilder();
        body.append("Hello,\n\n");
        body.append("You requested your Chorus Observe workspace ID(s).\n\n");
        body.append("Your workspace").append(users.size() > 1 ? "s" : "").append(":\n\n");

        for (User user : users) {
            var tenant = tenantRepository.findById(user.tenantId());
            String tenantName = tenant.map(t -> t.name()).orElse("Unknown workspace");
            body.append("  Workspace name: ").append(tenantName).append("\n");
            body.append("  Workspace ID:   ").append(user.tenantId()).append("\n\n");
        }

        body.append("Use the Workspace ID on the sign-in page together with your email and password.\n\n");
        body.append("If you did not request this, you can safely ignore this email.\n\n");
        body.append("— Chorus Observe");

        // DB SMTP config takes precedence over application.properties; any enabled tenant SMTP will do —
        // cross-tenant ordering is incidental here since the email body is identical for all tenants.
        SmtpConfigRepository.SmtpRow dbSmtp = users.stream()
            .map(u -> smtpConfigRepository.findByTenantId(u.tenantId()).orElse(null))
            .filter(s -> s != null && s.enabled() && !s.host().isBlank())
            .findFirst()
            .orElse(null);

        if (dbSmtp != null) {
            try {
                sendEmail(dbSmtp.host(), dbSmtp.port(), dbSmtp.useTls(),
                          dbSmtp.username(), dbSmtp.password(), dbSmtp.fromAddress(),
                          email, "Your Chorus Observe Workspace ID", body.toString());
                LOG.info("Workspace lookup email sent to {} (DB SMTP)", maskEmail(email));
            } catch (Exception e) {
                LOG.error("Failed to send workspace lookup email via DB SMTP", e);
            }
        } else {
            var smtp = properties.getSmtp();
            if (smtp.isConfigured()) {
                try {
                    sendEmail(smtp.getHost(), smtp.getPort(), smtp.isUseTls(),
                              smtp.getUsername(), smtp.getPassword(), smtp.getFrom(),
                              email, "Your Chorus Observe Workspace ID", body.toString());
                    LOG.info("Workspace lookup email sent to {} (properties SMTP)", maskEmail(email));
                } catch (Exception e) {
                    LOG.error("Failed to send workspace lookup email via properties SMTP", e);
                }
            } else {
                LOG.warn("SMTP not configured — workspace lookup email not sent. " +
                         "Workspace IDs for {}: {}", maskEmail(email),
                         users.stream().map(User::tenantId).toList());
            }
        }

        return true;
    }

    private boolean checkRateLimit(String key) {
        long now = Instant.now().getEpochSecond();
        long[] bucket = rateLimitBuckets.compute(key, (k, existing) -> {
            if (existing == null || now - existing[0] > WINDOW_SECONDS) {
                return new long[]{now, 1};
            }
            existing[1]++;
            return existing;
        });
        return bucket[1] <= MAX_REQUESTS;
    }

    private void sendEmail(String host, int port, boolean useTls, String username, String password,
                            String from, String to, String subject, String body) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth", username != null && !username.isBlank() ? "true" : "false");
        props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");

        final String finalUsername = username;
        final String finalPassword = password;
        jakarta.mail.Session session = jakarta.mail.Session.getInstance(props,
            finalUsername != null && !finalUsername.isBlank() ? new jakarta.mail.Authenticator() {
                @Override
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(finalUsername, finalPassword);
                }
            } : null);

        jakarta.mail.internet.MimeMessage message = new jakarta.mail.internet.MimeMessage(session);
        message.setFrom(new jakarta.mail.internet.InternetAddress(from));
        message.addRecipient(jakarta.mail.Message.RecipientType.TO, new jakarta.mail.internet.InternetAddress(to));
        message.setSubject(subject);
        message.setText(body);
        jakarta.mail.Transport.send(message);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***@***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
