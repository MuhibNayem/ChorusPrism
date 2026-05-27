package com.chorus.observe.api;

import com.chorus.observe.model.RoleMapping;
import com.chorus.observe.model.TenantOauthConfig;
import com.chorus.observe.model.TenantSamlConfig;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.security.saml2.SpKeyService;
import com.chorus.observe.service.Oauth2ConfigService;
import com.chorus.observe.service.SamlConfigService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified SSO provider management API consumed by the frontend.
 * Supports multiple IdPs per tenant across SAML 2.0 and OIDC protocols.
 */
@RestController
@RequestMapping("/api/v1/sso")
public class SsoController {

    private final SamlConfigService samlService;
    private final Oauth2ConfigService oauth2Service;
    private final SpKeyService spKeyService;

    public SsoController(@NonNull SamlConfigService samlService,
                         @NonNull Oauth2ConfigService oauth2Service,
                         @NonNull SpKeyService spKeyService) {
        this.samlService = samlService;
        this.oauth2Service = oauth2Service;
        this.spKeyService = spKeyService;
    }

    // ──────────────────────────────────────────────────────────────
    // Provider listing
    // ──────────────────────────────────────────────────────────────

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('admin')")
    public List<SsoProviderDto> listProviders() {
        String tenantId = TenantContext.getTenantId();
        List<SsoProviderDto> result = new ArrayList<>();
        samlService.findByTenant(tenantId).stream().map(SsoController::toDto).forEach(result::add);
        oauth2Service.findByTenant(tenantId).stream().map(SsoController::toDto).forEach(result::add);
        result.sort((a, b) -> a.createdAt().compareTo(b.createdAt()));
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // SAML CRUD
    // ──────────────────────────────────────────────────────────────

    @PostMapping("/providers/saml")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<SsoProviderDto> createSaml(@RequestBody @NonNull SamlRequest req) {
        TenantSamlConfig config = samlService.create(
            TenantContext.getTenantId(), req.providerName(),
            req.entityId(), req.signOnUrl(),
            req.signingCertThumbprint() != null ? req.signingCertThumbprint() : "",
            req.metadataUrl(), req.acsUrl(),
            req.defaultRole() != null ? req.defaultRole() : "VIEWER",
            req.roleMappings() != null ? req.roleMappings() : List.of(),
            req.allowedDomains() != null ? req.allowedDomains() : List.of(),
            req.attributeMappings() != null ? req.attributeMappings() : Map.of(),
            req.idpCertPem(), req.idpMetadataXml());
        return ResponseEntity.ok(toDto(config));
    }

    @PutMapping("/providers/saml/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<SsoProviderDto> updateSaml(@PathVariable @NonNull UUID id,
                                                      @RequestBody @NonNull SamlRequest req) {
        TenantSamlConfig config = samlService.update(
            id, TenantContext.getTenantId(), req.providerName(),
            req.entityId(), req.signOnUrl(),
            req.signingCertThumbprint() != null ? req.signingCertThumbprint() : "",
            req.metadataUrl(), req.acsUrl(),
            req.defaultRole() != null ? req.defaultRole() : "VIEWER",
            req.roleMappings() != null ? req.roleMappings() : List.of(),
            req.allowedDomains() != null ? req.allowedDomains() : List.of(),
            req.attributeMappings() != null ? req.attributeMappings() : Map.of(),
            req.idpCertPem(), req.idpMetadataXml());
        return ResponseEntity.ok(toDto(config));
    }

    @PostMapping("/providers/saml/{id}/metadata-upload")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<SsoProviderDto> uploadSamlMetadata(@PathVariable @NonNull UUID id,
                                                              @RequestBody @NonNull MetadataUploadRequest req) {
        TenantSamlConfig existing = samlService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SAML config not found: " + id));
        TenantSamlConfig updated = samlService.update(
            id, existing.tenantId(), existing.providerName(),
            existing.entityId(), existing.signOnUrl(), existing.signingCertThumbprint(),
            existing.metadataUrl(), existing.acsUrl(), existing.defaultRole(),
            existing.roleMappings(), existing.allowedDomains(), existing.attributeMappings(),
            existing.idpCertPem(), req.metadataXml());
        return ResponseEntity.ok(toDto(updated));
    }

    // ──────────────────────────────────────────────────────────────
    // OIDC CRUD
    // ──────────────────────────────────────────────────────────────

    @PostMapping("/providers/oidc")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<SsoProviderDto> createOidc(@RequestBody @NonNull OidcRequest req) {
        TenantOauthConfig config = oauth2Service.create(
            TenantContext.getTenantId(), req.providerName(),
            req.clientId(), req.clientSecret(), req.issuerUri(),
            req.scopes() != null ? req.scopes() : List.of("openid", "email", "profile"),
            req.defaultRole() != null ? req.defaultRole() : "VIEWER",
            req.roleMappings() != null ? req.roleMappings() : List.of(),
            req.allowedDomains() != null ? req.allowedDomains() : List.of(),
            req.attributeMappings() != null ? req.attributeMappings() : Map.of());
        return ResponseEntity.ok(toDto(config));
    }

    @PutMapping("/providers/oidc/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<SsoProviderDto> updateOidc(@PathVariable @NonNull UUID id,
                                                      @RequestBody @NonNull OidcRequest req) {
        TenantOauthConfig config = oauth2Service.update(
            id, TenantContext.getTenantId(), req.providerName(),
            req.clientId(), req.clientSecret(), req.issuerUri(),
            req.scopes() != null ? req.scopes() : List.of("openid", "email", "profile"),
            req.defaultRole() != null ? req.defaultRole() : "VIEWER",
            req.roleMappings() != null ? req.roleMappings() : List.of(),
            req.allowedDomains() != null ? req.allowedDomains() : List.of(),
            req.attributeMappings() != null ? req.attributeMappings() : Map.of());
        return ResponseEntity.ok(toDto(config));
    }

    // ──────────────────────────────────────────────────────────────
    // Shared operations (SAML + OIDC)
    // ──────────────────────────────────────────────────────────────

    @DeleteMapping("/providers/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> deleteProvider(@PathVariable @NonNull UUID id,
                                               @RequestParam @NonNull String protocol) {
        if ("SAML".equalsIgnoreCase(protocol)) {
            samlService.delete(id);
        } else if ("OIDC".equalsIgnoreCase(protocol)) {
            oauth2Service.delete(id);
        } else {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/providers/{id}/toggle")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> toggleProvider(@PathVariable @NonNull UUID id,
                                               @RequestParam @NonNull String protocol,
                                               @RequestParam boolean enabled) {
        if ("SAML".equalsIgnoreCase(protocol)) {
            samlService.toggle(id, enabled);
        } else if ("OIDC".equalsIgnoreCase(protocol)) {
            oauth2Service.toggle(id, enabled);
        } else {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────
    // SP Metadata
    // ──────────────────────────────────────────────────────────────

    @GetMapping("/sp-metadata")
    @PreAuthorize("hasAuthority('admin')")
    public SpMetadataDto getSpMetadata(@RequestParam(required = false) String baseUrl) {
        String tenantId = TenantContext.getTenantId();
        String base = baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://your-chorus-instance";
        String entityId = base + "/saml2/service-provider-metadata/" + tenantId + "__default";
        String acsUrl = base + "/login/saml2/sso/" + tenantId + "__{providerName}";
        String scimEndpoint = base + "/scim/v2/";

        // Return the SP cert PEM so admins can configure it in their IdP
        String spCertPem = null;
        try {
            SpKeyService.SpKeyPair keys = spKeyService.getOrGenerate(tenantId);
            spCertPem = pemFromCert(keys.certificate().getEncoded());
        } catch (Exception ignored) {}

        return new SpMetadataDto(acsUrl, entityId, scimEndpoint, spCertPem);
    }

    @PostMapping("/sp-keys/rotate")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> rotateSpKey() {
        spKeyService.rotate(TenantContext.getTenantId());
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────
    // Health
    // ──────────────────────────────────────────────────────────────

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('admin')")
    public List<ProviderHealthDto> getHealth() {
        String tenantId = TenantContext.getTenantId();
        List<ProviderHealthDto> result = new ArrayList<>();

        samlService.findByTenant(tenantId).forEach(cfg ->
            result.add(new ProviderHealthDto(cfg.id(), cfg.providerName(), "SAML",
                cfg.enabled() ? "ok" : "disabled",
                cfg.metadataUrl(), null)));

        oauth2Service.findByTenant(tenantId).forEach(cfg ->
            result.add(new ProviderHealthDto(cfg.id(), cfg.providerName(), "OIDC",
                cfg.enabled() ? "ok" : "disabled",
                cfg.issuerUri() + "/.well-known/openid-configuration", null)));

        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private static @NonNull SsoProviderDto toDto(@NonNull TenantSamlConfig c) {
        return new SsoProviderDto(c.id(), c.providerName(), "SAML", c.enabled(),
            c.entityId(), c.signOnUrl(), c.signingCertThumbprint(), c.metadataUrl(), c.acsUrl(),
            null, null, null,
            c.defaultRole(), c.roleMappings(), c.allowedDomains(), c.attributeMappings(),
            c.idpCertPem() != null, c.idpMetadataXml() != null,
            c.createdAt(), c.updatedAt());
    }

    private static @NonNull SsoProviderDto toDto(@NonNull TenantOauthConfig c) {
        return new SsoProviderDto(c.id(), c.providerName(), "OIDC", c.enabled(),
            null, null, null, null, null,
            c.clientId(), c.issuerUri(), c.scopes(),
            c.defaultRole(), c.roleMappings(), c.allowedDomains(), c.attributeMappings(),
            false, false,
            c.createdAt(), c.updatedAt());
    }

    private static @NonNull String pemFromCert(byte[] derBytes) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(derBytes);
        return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----";
    }

    // ──────────────────────────────────────────────────────────────
    // DTOs
    // ──────────────────────────────────────────────────────────────

    public record SsoProviderDto(
        @Nullable UUID id,
        @NonNull String providerName,
        @NonNull String protocol,
        boolean enabled,
        // SAML-specific
        @Nullable String entityId,
        @Nullable String signOnUrl,
        @Nullable String signingCertThumbprint,
        @Nullable String metadataUrl,
        @Nullable String acsUrl,
        // OIDC-specific
        @Nullable String clientId,
        @Nullable String issuerUri,
        @Nullable List<String> scopes,
        // Common
        @NonNull String defaultRole,
        @NonNull List<RoleMapping> roleMappings,
        @NonNull List<String> allowedDomains,
        @NonNull Map<String, String> attributeMappings,
        boolean hasCertPem,
        boolean hasMetadataXml,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
    ) {}

    public record SamlRequest(
        String providerName,
        String entityId,
        String signOnUrl,
        String signingCertThumbprint,
        String metadataUrl,
        String acsUrl,
        String defaultRole,
        List<RoleMapping> roleMappings,
        List<String> allowedDomains,
        Map<String, String> attributeMappings,
        String idpCertPem,
        String idpMetadataXml
    ) {}

    public record OidcRequest(
        String providerName,
        String clientId,
        String clientSecret,
        String issuerUri,
        List<String> scopes,
        String defaultRole,
        List<RoleMapping> roleMappings,
        List<String> allowedDomains,
        Map<String, String> attributeMappings
    ) {}

    public record MetadataUploadRequest(String metadataXml) {}

    public record SpMetadataDto(
        @NonNull String acsUrl,
        @NonNull String entityId,
        @Nullable String scimEndpoint,
        @Nullable String spCertPem
    ) {}

    public record ProviderHealthDto(
        @Nullable UUID id,
        @NonNull String providerName,
        @NonNull String protocol,
        @NonNull String status,
        @Nullable String metadataUrl,
        @Nullable String message
    ) {}
}
