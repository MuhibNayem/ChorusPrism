package com.chorus.observe.api;

import com.chorus.observe.model.Tenant;
import com.chorus.observe.service.TenantService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(@NonNull TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<?> createTenant(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        Tenant tenant = tenantService.createTenant(name);
        return ResponseEntity.ok(Map.of("tenantId", tenant.tenantId(), "name", tenant.name()));
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> listTenants() {
        return ResponseEntity.ok(tenantService.listTenants());
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<?> getTenant(@PathVariable String tenantId) {
        return tenantService.getTenant(tenantId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
