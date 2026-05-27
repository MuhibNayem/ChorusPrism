package com.chorus.observe.api;

import com.chorus.observe.model.AuditLog;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.persistence.AuditLogRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(@NonNull AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<PagedResult<AuditLogResponse>> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String tenantId = TenantContext.getTenantId();
        int offset = page * size;
        List<AuditLogResponse> logs = auditLogRepository.findByTenantWithUsername(tenantId, size, offset)
            .stream()
            .map(e -> AuditLogResponse.from(e.log(), e.username()))
            .toList();
        long total = auditLogRepository.countByTenant(tenantId);
        return ResponseEntity.ok(new PagedResult<>(logs, total, page, size));
    }

    /** Frontend-facing DTO — includes resolved username alongside all AuditLog fields. */
    public record AuditLogResponse(
        @NonNull String logId,
        @NonNull String tenantId,
        @Nullable String userId,
        @Nullable String username,
        @NonNull String action,
        @NonNull String resourceType,
        @Nullable String resourceId,
        @Nullable String ipAddress,
        @Nullable String userAgent,
        boolean success,
        @NonNull Map<String, Object> details,
        @NonNull Instant createdAt
    ) {
        static AuditLogResponse from(@NonNull AuditLog log, @Nullable String username) {
            return new AuditLogResponse(
                log.logId(), log.tenantId(), log.userId(), username,
                log.action(), log.resourceType(), log.resourceId(),
                log.ipAddress(), log.userAgent(), log.success(),
                log.details(), log.createdAt()
            );
        }
    }
}
