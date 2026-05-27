package com.chorus.observe.api;

import com.chorus.observe.model.NotificationChannel;
import com.chorus.observe.notification.NotificationService;
import com.chorus.observe.persistence.AlertRuleChannelRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API for managing notification channels.
 *
 * <pre>
 * POST   /api/v1/notification-channels              — create
 * GET    /api/v1/notification-channels              — list (tenant-scoped)
 * GET    /api/v1/notification-channels/{id}         — get one
 * PUT    /api/v1/notification-channels/{id}         — update name / config / enabled
 * DELETE /api/v1/notification-channels/{id}         — delete
 * POST   /api/v1/notification-channels/{id}/test    — verify connectivity
 * PATCH  /api/v1/notification-channels/{id}/toggle  — enable / disable
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/notification-channels")
public class NotificationChannelController {

    private final NotificationService notificationService;
    private final AlertRuleChannelRepository alertRuleChannelRepository;

    public NotificationChannelController(@NonNull NotificationService notificationService,
                                          @NonNull AlertRuleChannelRepository alertRuleChannelRepository) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.alertRuleChannelRepository = Objects.requireNonNull(alertRuleChannelRepository);
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAuthority('alerts:write')")
    public ResponseEntity<?> createChannel(@RequestBody @NonNull Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        String name = (String) request.get("name");
        String type = (String) request.get("channelType");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) request.getOrDefault("config", Map.of());
        NotificationChannel channel = notificationService.createChannel(tenantId, name,
            NotificationChannel.ChannelType.valueOf(type), config);
        return ResponseEntity.ok(Map.of("channelId", channel.channelId()));
    }

    // -----------------------------------------------------------------------
    // List
    // -----------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAuthority('alerts:read')")
    public ResponseEntity<List<NotificationChannel>> listChannels() {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(notificationService.listChannels(tenantId));
    }

    // -----------------------------------------------------------------------
    // Get one
    // -----------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('alerts:read')")
    public ResponseEntity<NotificationChannel> getChannel(@PathVariable @NonNull String id) {
        return notificationService.getChannel(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // -----------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('alerts:write')")
    public ResponseEntity<?> updateChannel(@PathVariable @NonNull String id,
                                            @RequestBody @NonNull Map<String, Object> request) {
        return notificationService.getChannel(id)
            .map(existing -> {
                String name = request.containsKey("name") ? (String) request.get("name") : existing.name();
                boolean enabled = request.containsKey("enabled")
                    ? Boolean.parseBoolean(String.valueOf(request.get("enabled")))
                    : existing.enabled();
                @SuppressWarnings("unchecked")
                Map<String, Object> config = request.containsKey("config")
                    ? (Map<String, Object>) request.get("config")
                    : existing.config();
                NotificationChannel updated = new NotificationChannel(
                    existing.channelId(), existing.tenantId(), name, existing.channelType(),
                    config, enabled, existing.lastUsedAt(), existing.createdAt(), java.time.Instant.now());
                notificationService.updateChannel(updated);
                return ResponseEntity.ok(updated);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // -----------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('alerts:write')")
    public ResponseEntity<Void> deleteChannel(@PathVariable @NonNull String id) {
        notificationService.deleteChannel(id);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Test
    // -----------------------------------------------------------------------

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('alerts:write')")
    public ResponseEntity<?> testChannel(@PathVariable @NonNull String id) {
        return notificationService.getChannel(id)
            .map(channel -> {
                NotificationService.TestResult result = notificationService.testChannel(channel);
                if (result.success()) {
                    return ResponseEntity.ok(Map.of("success", true));
                }
                return ResponseEntity.ok(Map.of("success", false, "error", result.error() != null ? result.error() : "Unknown error"));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // -----------------------------------------------------------------------
    // Toggle
    // -----------------------------------------------------------------------

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('alerts:write')")
    public ResponseEntity<?> toggleChannel(@PathVariable @NonNull String id,
                                            @RequestBody @NonNull Map<String, Object> request) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(request.getOrDefault("enabled", true)));
        try {
            NotificationChannel toggled = notificationService.toggleChannel(id, enabled);
            return ResponseEntity.ok(Map.of("channelId", toggled.channelId(), "enabled", toggled.enabled()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
