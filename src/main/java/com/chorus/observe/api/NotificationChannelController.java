package com.chorus.observe.api;

import com.chorus.observe.model.NotificationChannel;
import com.chorus.observe.notification.NotificationService;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notification-channels")
public class NotificationChannelController {

    private final NotificationService notificationService;

    public NotificationChannelController(@NonNull NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<?> createChannel(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        String name = (String) request.get("name");
        String type = (String) request.get("channelType");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) request.getOrDefault("config", Map.of());
        NotificationChannel channel = notificationService.createChannel(tenantId, name,
            NotificationChannel.ChannelType.valueOf(type), config);
        return ResponseEntity.ok(Map.of("channelId", channel.channelId()));
    }
}
