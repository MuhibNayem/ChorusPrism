package com.chorus.observe.api;

import com.chorus.observe.model.AuditLog;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.persistence.AuditLogRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(@NonNull AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<PagedResult<AuditLog>> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String tenantId = TenantContext.getTenantId();
        int offset = page * size;
        List<AuditLog> logs = auditLogRepository.findByTenant(tenantId, size, offset);
        long total = auditLogRepository.countByTenant(tenantId);
        return ResponseEntity.ok(new PagedResult<>(logs, total, page, size));
    }
}
