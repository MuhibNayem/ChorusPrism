package com.chorus.observe.audit;

import com.chorus.observe.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Aspect
public class AuditLogAspect {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditLogService auditLogService;

    public AuditLogAspect(@NonNull AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController) && execution(public * com.chorus.observe.api.*Controller.*(..))")
    public Object auditControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!TenantContext.isAuthenticated()) {
            return joinPoint.proceed();
        }

        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String action = className.replace("Controller", "").toUpperCase() + "." + methodName;
        String resourceType = className.replace("Controller", "").toLowerCase();

        String ipAddress = null;
        String userAgent = null;
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ipAddress = request.getRemoteAddr();
                userAgent = request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            // ignore
        }

        try {
            Object result = joinPoint.proceed();
            auditLogService.log(action, resourceType, null, null, null, true, ipAddress, userAgent);
            return result;
        } catch (Exception ex) {
            auditLogService.log(action, resourceType, null, null, Map.of("error", ex.getMessage()), false, ipAddress, userAgent);
            throw ex;
        }
    }
}
