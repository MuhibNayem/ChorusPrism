package com.chorus.observe.security;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Thread-local holder for the current tenant and authenticated user.
 * Populated by {@link JwtAuthFilter} or {@link ApiKeyAuthFilter}.
 */
public final class TenantContext {

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(@NonNull String tenantId, @Nullable String userId, @Nullable String apiKeyHash) {
        CONTEXT.set(new Context(tenantId, userId, apiKeyHash));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static @NonNull String getTenantId() {
        Context ctx = CONTEXT.get();
        if (ctx == null || ctx.tenantId == null) {
            throw new IllegalStateException("No tenant context available. Authentication filter may not be configured.");
        }
        return ctx.tenantId;
    }

    public static @Nullable String getTenantIdOrNull() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.tenantId : null;
    }

    public static @Nullable String getUserId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.userId : null;
    }

    public static @Nullable String getApiKeyHash() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.apiKeyHash : null;
    }

    public static boolean isAuthenticated() {
        return CONTEXT.get() != null;
    }

    private record Context(String tenantId, String userId, String apiKeyHash) {}
}
