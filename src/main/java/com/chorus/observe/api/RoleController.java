package com.chorus.observe.api;

import com.chorus.observe.model.Role;
import com.chorus.observe.service.RoleService;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(@NonNull RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    public ResponseEntity<?> createRole(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) request.getOrDefault("permissions", List.of());
        Role role = roleService.createRole(tenantId, (String) request.get("name"), permissions, (String) request.get("description"));
        return ResponseEntity.ok(Map.of("roleId", role.roleId(), "name", role.name()));
    }

    @GetMapping
    public ResponseEntity<List<Role>> listRoles() {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(roleService.listRolesByTenant(tenantId));
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<?> getRole(@PathVariable String roleId) {
        return roleService.getRole(roleId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<?> deleteRole(@PathVariable String roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}
