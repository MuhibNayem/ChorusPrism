package com.chorus.observe.api;

import com.chorus.observe.persistence.SmtpConfigRepository;
import com.chorus.observe.persistence.UserRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Properties;

@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/api/v1/settings/smtp")
public class SmtpConfigController {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpConfigController.class);

    private static final SmtpConfigResponse EMPTY =
        new SmtpConfigResponse("", 587, "", "", "noreply@chorus.observe", true, false);

    private final SmtpConfigRepository smtpConfigRepository;
    private final UserRepository userRepository;

    public SmtpConfigController(@NonNull SmtpConfigRepository smtpConfigRepository,
                                 @NonNull UserRepository userRepository) {
        this.smtpConfigRepository = smtpConfigRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<SmtpConfigResponse> getConfig() {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(
            smtpConfigRepository.findByTenantId(tenantId)
                .map(r -> new SmtpConfigResponse(
                    r.host(), r.port(), r.username(),
                    mask(r.password()), r.fromAddress(), r.useTls(), r.enabled()))
                .orElse(EMPTY));
    }

    @PutMapping
    public ResponseEntity<SmtpConfigResponse> saveConfig(@RequestBody SmtpConfigRequest body) {
        String tenantId = TenantContext.getTenantId();
        // If password is a mask placeholder, load existing password from DB
        String password = body.password();
        if (password == null || password.startsWith("•")) {
            password = smtpConfigRepository.findByTenantId(tenantId)
                .map(SmtpConfigRepository.SmtpRow::password)
                .orElse("");
        }
        smtpConfigRepository.save(tenantId, new SmtpConfigRepository.SmtpRow(
            nullToEmpty(body.host()), body.port() > 0 ? body.port() : 587,
            nullToEmpty(body.username()), password,
            nullToEmpty(body.from()), body.useTls(), body.enabled()));
        return ResponseEntity.ok(new SmtpConfigResponse(
            body.host(), body.port(), body.username(),
            mask(password), body.from(), body.useTls(), body.enabled()));
    }

    @PostMapping("/test")
    public ResponseEntity<?> testConfig(@RequestBody(required = false) Map<String, Object> body) {
        String tenantId = TenantContext.getTenantId();
        SmtpConfigRepository.SmtpRow row = smtpConfigRepository.findByTenantId(tenantId).orElse(null);
        if (row == null || !row.enabled() || row.host().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "No enabled SMTP config found. Save and enable a configuration first."));
        }

        // Default test recipient to the authenticated user's email address
        String to = body != null ? (String) body.get("to") : null;
        if (to == null || to.isBlank()) {
            String userId = TenantContext.getUserId();
            if (userId != null) {
                to = userRepository.findById(userId).map(u -> u.email()).orElse(null);
            }
        }
        if (to == null || to.isBlank()) {
            to = row.fromAddress();
        }

        try {
            sendTestEmail(row, to);
            LOG.info("SMTP test email sent to {} for tenant {}", to, tenantId);
            return ResponseEntity.ok(Map.of("message", "Test email sent to " + to));
        } catch (Exception e) {
            LOG.warn("SMTP test failed for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "SMTP connection failed: " + e.getMessage()));
        }
    }

    private void sendTestEmail(SmtpConfigRepository.SmtpRow row, String to) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth", !row.username().isBlank() ? "true" : "false");
        props.put("mail.smtp.starttls.enable", String.valueOf(row.useTls()));
        props.put("mail.smtp.host", row.host());
        props.put("mail.smtp.port", String.valueOf(row.port()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");

        jakarta.mail.Session session = jakarta.mail.Session.getInstance(props,
            !row.username().isBlank() ? new jakarta.mail.Authenticator() {
                @Override
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(row.username(), row.password());
                }
            } : null);

        jakarta.mail.internet.MimeMessage message = new jakarta.mail.internet.MimeMessage(session);
        message.setFrom(new jakarta.mail.internet.InternetAddress(row.fromAddress()));
        message.addRecipient(jakarta.mail.Message.RecipientType.TO,
            new jakarta.mail.internet.InternetAddress(to));
        message.setSubject("Chorus Observe — SMTP configuration test");
        message.setText("Your SMTP configuration is working correctly.\n\n— Chorus Observe");
        jakarta.mail.Transport.send(message);
    }

    private static String mask(String v) {
        if (v == null || v.isBlank()) return "";
        return "•".repeat(Math.min(v.length(), 16));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    record SmtpConfigResponse(
        String host, int port, String username, String password,
        String from, boolean useTls, boolean enabled) {}

    record SmtpConfigRequest(
        String host, int port, String username, String password,
        String from, boolean useTls, boolean enabled) {}
}
