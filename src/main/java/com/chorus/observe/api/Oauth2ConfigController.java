package com.chorus.observe.api;

import com.chorus.observe.model.TenantOauthConfig;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.Oauth2ConfigService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/oauth2-configs")
public class Oauth2ConfigController {

    private final Oauth2ConfigService service;

    public Oauth2ConfigController(@NonNull Oauth2ConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('admin')")
    public List<TenantOauthConfig> list() {
        return service.findByTenant(TenantContext.getTenantId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<TenantOauthConfig> create(@RequestBody @NonNull CreateRequest request) {
        TenantOauthConfig config = service.create(
            TenantContext.getTenantId(), request.providerName(), request.clientId(),
            request.clientSecret(), request.issuerUri(), request.scopes(),
            request.defaultRole() != null ? request.defaultRole() : "VIEWER",
            List.of(), List.of(), Map.of());
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
        String clientId,
        String clientSecret,
        String issuerUri,
        List<String> scopes,
        String defaultRole
    ) {}
}
