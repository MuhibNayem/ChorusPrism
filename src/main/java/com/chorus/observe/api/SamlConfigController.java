package com.chorus.observe.api;

import com.chorus.observe.model.TenantSamlConfig;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.SamlConfigService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/saml-configs")
public class SamlConfigController {

    private final SamlConfigService service;

    public SamlConfigController(@NonNull SamlConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('admin')")
    public List<TenantSamlConfig> list() {
        return service.findByTenant(TenantContext.getTenantId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<TenantSamlConfig> create(@RequestBody @NonNull CreateRequest request) {
        TenantSamlConfig config = service.create(
            TenantContext.getTenantId(), request.providerName(), request.entityId(),
            request.signOnUrl(), request.signingCertThumbprint(), request.metadataUrl(),
            request.acsUrl(), request.defaultRole() != null ? request.defaultRole() : "VIEWER");
        return ResponseEntity.ok(config);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> delete(@PathVariable @NonNull UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateRequest(
        String providerName,
        String entityId,
        String signOnUrl,
        String signingCertThumbprint,
        String metadataUrl,
        String acsUrl,
        String defaultRole
    ) {}
}
