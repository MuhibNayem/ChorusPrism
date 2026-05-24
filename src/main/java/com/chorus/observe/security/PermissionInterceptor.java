package com.chorus.observe.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Enforces {@link RequirePermission} annotations on controller methods.
 * <p>
 * Runs after the DispatcherServlet resolves the handler, so it has direct
 * access to the annotated method. Checks the {@code scopes} request attribute
 * populated by {@link JwtAuthFilter} or {@link ApiKeyAuthFilter}.
 */
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            return true;
        }

        if (!TenantContext.isAuthenticated()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return false;
        }

        @SuppressWarnings("unchecked")
        Set<String> scopes = (Set<String>) request.getAttribute("scopes");
        if (scopes == null) {
            scopes = Set.of();
        }

        String required = annotation.value();
        if (!scopes.contains(required) && !scopes.contains(Permission.ADMIN)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permissions: " + required);
            return false;
        }

        return true;
    }
}
