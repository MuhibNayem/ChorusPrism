package com.chorus.observe.audit;

import com.chorus.observe.model.AuditLog;
import com.chorus.observe.persistence.AuditLogRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class AuditLogService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(@NonNull AuditLogRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository);
    }

    public void log(@NonNull String action, @NonNull String resourceType, @Nullable String resourceId,
                    @Nullable Map<String, Object> oldValue, @Nullable Map<String, Object> newValue,
                    boolean success, @Nullable String ipAddress, @Nullable String userAgent) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId == null) return;
        String userId = TenantContext.getUserId();
        String logId = "audit-" + UUID.randomUUID().toString().substring(0, 8);
        AuditLog log = new AuditLog(logId, tenantId, userId, action, resourceType, resourceId,
            oldValue, newValue, ipAddress, userAgent, success, Map.of(), Instant.now());
        auditLogRepository.save(log);
    }

    public void log(@NonNull String action, @NonNull String resourceType, @Nullable String resourceId) {
        log(action, resourceType, resourceId, null, null, true, null, null);
    }
}
