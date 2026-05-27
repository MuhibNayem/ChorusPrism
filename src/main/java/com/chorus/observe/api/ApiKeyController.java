package com.chorus.observe.api;

import com.chorus.observe.model.ApiKey;
import com.chorus.observe.persistence.ApiKeyRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings/api-keys")
@PreAuthorize("hasAuthority('admin')")
public class ApiKeyController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyController(@NonNull ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys() {
        String tenantId = TenantContext.getTenantId();
        List<ApiKeyResponse> keys = apiKeyRepository.findByTenant(tenantId).stream()
            .filter(k -> !k.isRevoked())
            .map(ApiKeyResponse::from)
            .toList();
        return ResponseEntity.ok(keys);
    }

    @PostMapping
    public ResponseEntity<ApiKeyCreated> createKey(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();
        String name = request.get("name") instanceof String s ? s.strip() : "API Key";
        List<String> scopes = request.get("scopes") instanceof List<?> list
            ? list.stream().map(Object::toString).toList()
            : List.of("read");

        // Generate key: chs_ + 32 random hex chars
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String rawKey = "chs_" + HexFormat.of().formatHex(bytes);
        String prefix = rawKey.substring(0, Math.min(12, rawKey.length()));
        String keyHash = sha256Hex(rawKey);

        ApiKey apiKey = new ApiKey(keyHash, tenantId, userId, name, scopes,
            null, null, Instant.now(), null);
        apiKeyRepository.save(apiKey);
        apiKeyRepository.saveKeyPrefix(keyHash, prefix);

        return ResponseEntity.ok(new ApiKeyCreated(
            keyHash, name, prefix, rawKey, scopes, apiKey.createdAt()));
    }

    @DeleteMapping("/{keyHash}")
    public ResponseEntity<Void> revokeKey(@PathVariable String keyHash) {
        String tenantId = TenantContext.getTenantId();
        // Verify key belongs to this tenant before revoking
        apiKeyRepository.findByKeyHash(keyHash)
            .filter(k -> k.tenantId().equals(tenantId))
            .ifPresent(k -> apiKeyRepository.revoke(keyHash, Instant.now()));
        return ResponseEntity.noContent().build();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record ApiKeyResponse(
        @NonNull  String keyHash,
        @NonNull  String name,
        @Nullable String keyPrefix,
        @NonNull  List<String> scopes,
        @Nullable Instant expiresAt,
        @Nullable Instant lastUsedAt,
        @NonNull  Instant createdAt,
        boolean expired
    ) {
        static ApiKeyResponse from(@NonNull ApiKey k) {
            return new ApiKeyResponse(
                k.keyHash(), k.name(), k.keyPrefix(), k.scopes(),
                k.expiresAt(), k.lastUsedAt(), k.createdAt(), k.isExpired());
        }
    }

    public record ApiKeyCreated(
        @NonNull  String keyHash,
        @NonNull  String name,
        @NonNull  String keyPrefix,
        @NonNull  String key,
        @NonNull  List<String> scopes,
        @NonNull  Instant createdAt
    ) {}
}
