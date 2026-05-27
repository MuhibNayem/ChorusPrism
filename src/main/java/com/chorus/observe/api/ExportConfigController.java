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

        // Load existing config to preserve credentials when placeholder masks are submitted
        ExportConfig existing = exportConfigRepository.findByTenantAndType(tenantId, ExportConfig.DestinationType.S3)
            .orElse(null);
        String configId = existing != null ? existing.configId()
            : (String) request.getOrDefault("configId", UUID.randomUUID().toString());

        String accessKeyId = resolveCredential(
            (String) request.get("accessKeyId"),
            existing != null ? existing.accessKeyId() : null);
        String secretAccessKey = resolveCredential(
            (String) request.get("secretAccessKey"),
            existing != null ? existing.secretAccessKey() : null);

        ExportConfig config = new ExportConfig(
            configId,
            tenantId,
            ExportConfig.DestinationType.S3,
            (String) request.get("endpointUrl"),
            (String) request.getOrDefault("region", "us-east-1"),
            (String) request.get("bucketName"),
            encryptionService.encrypt(accessKeyId != null ? accessKeyId : ""),
            encryptionService.encrypt(secretAccessKey != null ? secretAccessKey : ""),
            (String) request.getOrDefault("pathPrefix", ""),
            (Boolean) request.getOrDefault("enabled", true),
            existing != null ? existing.createdAt() : Instant.now(),
            Instant.now()
        );
        exportConfigRepository.save(config);
        return ResponseEntity.ok(maskSecrets(config));
    }

    /**
     * Returns the existing encrypted credential when the submitted value is a display mask placeholder
     * (format: 4chars + *** + 4chars, or literal "***"). Otherwise encrypts the new submitted value.
     */
    private String resolveCredential(String submitted, String existingEncrypted) {
        if (isMaskPlaceholder(submitted)) {
            return existingEncrypted != null ? encryptionService.decrypt(existingEncrypted) : "";
        }
        return submitted != null ? submitted : "";
    }

    private boolean isMaskPlaceholder(String value) {
        if (value == null) return false;
        if ("***".equals(value)) return true;
        // Mask format is exactly: 4 chars + *** + 4 chars = 11 chars total
        return value.length() == 11 && value.substring(4, 7).equals("***");
    }

    private ExportConfig maskSecrets(ExportConfig config) {
        return new ExportConfig(
            config.configId(), config.tenantId(), config.destinationType(),
            config.endpointUrl(), config.region(), config.bucketName(),
            maskDecrypted(config.accessKeyId()), maskDecrypted(config.secretAccessKey()),
            config.pathPrefix(), config.enabled(), config.createdAt(), config.updatedAt()
        );
    }

    private String maskDecrypted(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) return "";
        try {
            String plaintext = encryptionService.decrypt(encryptedValue);
            if (plaintext.length() < 8) return "***";
            return plaintext.substring(0, 4) + "***" + plaintext.substring(plaintext.length() - 4);
        } catch (Exception e) {
            return "***";
        }
    }
}
