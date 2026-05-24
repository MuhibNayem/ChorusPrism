package com.chorus.observe.api;

import com.chorus.observe.export.CredentialEncryptionService;
import com.chorus.observe.model.ExportConfig;
import com.chorus.observe.persistence.ExportConfigRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/api/v1/export-configs")
public class ExportConfigController {

    private final ExportConfigRepository exportConfigRepository;
    private final CredentialEncryptionService encryptionService;

    public ExportConfigController(@NonNull ExportConfigRepository exportConfigRepository,
                                   @NonNull CredentialEncryptionService encryptionService) {
        this.exportConfigRepository = exportConfigRepository;
        this.encryptionService = encryptionService;
    }

    @GetMapping
    public ResponseEntity<?> getConfig() {
        String tenantId = TenantContext.getTenantId();
        return exportConfigRepository.findByTenantAndType(tenantId, ExportConfig.DestinationType.S3)
            .map(this::maskSecrets)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> saveConfig(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        String configId = (String) request.getOrDefault("configId", UUID.randomUUID().toString());

        ExportConfig config = new ExportConfig(
            configId,
            tenantId,
            ExportConfig.DestinationType.S3,
            (String) request.get("endpointUrl"),
            (String) request.getOrDefault("region", "us-east-1"),
            (String) request.get("bucketName"),
            encryptionService.encrypt((String) request.get("accessKeyId")),
            encryptionService.encrypt((String) request.get("secretAccessKey")),
            (String) request.getOrDefault("pathPrefix", ""),
            (Boolean) request.getOrDefault("enabled", true),
            Instant.now(),
            Instant.now()
        );
        exportConfigRepository.save(config);
        return ResponseEntity.ok(maskSecrets(config));
    }

    private ExportConfig maskSecrets(ExportConfig config) {
        return new ExportConfig(
            config.configId(), config.tenantId(), config.destinationType(),
            config.endpointUrl(), config.region(), config.bucketName(),
            mask(config.accessKeyId()), mask(config.secretAccessKey()),
            config.pathPrefix(), config.enabled(), config.createdAt(), config.updatedAt()
        );
    }

    private String mask(String value) {
        if (value == null || value.length() < 8) return "***";
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}
