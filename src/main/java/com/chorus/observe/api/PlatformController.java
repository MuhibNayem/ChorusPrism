package com.chorus.observe.api;

import com.chorus.observe.config.ChorusObserveProperties;
import com.chorus.observe.persistence.TenantSettingsRepository;
import com.chorus.observe.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings/platform")
public class PlatformController {

    private static final String KEY_PUBLIC_URL = "platform.publicUrl";

    private final ChorusObserveProperties properties;
    private final TenantSettingsRepository settingsRepo;

    public PlatformController(
            @NonNull ChorusObserveProperties properties,
            @NonNull TenantSettingsRepository settingsRepo) {
        this.properties = properties;
        this.settingsRepo = settingsRepo;
    }

    @GetMapping
    public ResponseEntity<PlatformInfo> getPlatform(HttpServletRequest req) {
        String tenantId = TenantContext.getTenantId();
        String storedUrl = settingsRepo.find(tenantId, KEY_PUBLIC_URL).orElse("");
        String publicUrl = resolvePublicUrl(storedUrl, req);

        String otlpHttpUrl = publicUrl + "/v1/traces";
        boolean grpcEnabled = properties.getGrpc().isEnabled();
        int grpcPort = properties.getGrpc().getPort();
        String grpcHost = extractHost(publicUrl);
        String otlpGrpcEndpoint = grpcEnabled ? grpcHost + ":" + grpcPort : null;

        String storageBackend = properties.getStorage().getSpanStore();
        String ingestModes = grpcEnabled ? "OTLP HTTP + gRPC" : "OTLP HTTP";

        return ResponseEntity.ok(new PlatformInfo(
            storedUrl,
            publicUrl,
            otlpHttpUrl,
            otlpGrpcEndpoint,
            grpcPort,
            grpcEnabled,
            "1.0.0",
            storageBackend,
            ingestModes
        ));
    }

    @PutMapping
    public ResponseEntity<Void> updatePlatform(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        String publicUrl = request.get("publicUrl") instanceof String s ? s.strip() : "";
        settingsRepo.upsert(tenantId, KEY_PUBLIC_URL, publicUrl);
        return ResponseEntity.noContent().build();
    }

    private String resolvePublicUrl(@NonNull String storedUrl, @NonNull HttpServletRequest req) {
        if (!storedUrl.isBlank()) return storedUrl;
        // Try frontend URL from properties first
        String frontendUrl = properties.getFrontend().getUrl();
        if (!frontendUrl.isBlank() && !frontendUrl.contains("localhost")) return frontendUrl;
        // Fall back to the inbound request origin
        int port = req.getServerPort();
        String scheme = req.getScheme();
        boolean defaultPort = (port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme));
        return scheme + "://" + req.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private static String extractHost(String url) {
        try {
            return new java.net.URI(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    public record PlatformInfo(
        @Nullable String configuredPublicUrl,
        @NonNull  String resolvedPublicUrl,
        @NonNull  String otlpHttpUrl,
        @Nullable String otlpGrpcEndpoint,
        int grpcPort,
        boolean grpcEnabled,
        @NonNull  String version,
        @NonNull  String storageBackend,
        @NonNull  String ingestModes
    ) {}
}
