package com.chorus.observe.security;

/**
 * Permissions for RBAC.
 */
public final class Permission {
    private Permission() {}

    public static final String RUNS_READ = "runs:read";
    public static final String RUNS_WRITE = "runs:write";
    public static final String SPANS_READ = "spans:read";
    public static final String EVALS_READ = "evals:read";
    public static final String EVALS_WRITE = "evals:write";
    public static final String ALERTS_READ = "alerts:read";
    public static final String ALERTS_WRITE = "alerts:write";
    public static final String DASHBOARDS_READ = "dashboards:read";
    public static final String DASHBOARDS_WRITE = "dashboards:write";
    public static final String USERS_READ = "users:read";
    public static final String USERS_WRITE = "users:write";
    public static final String SETTINGS_READ = "settings:read";
    public static final String SETTINGS_WRITE = "settings:write";
    public static final String AUDIT_READ = "audit:read";
    public static final String EXPORT_READ = "export:read";
    public static final String EXPORT_WRITE = "export:write";
    public static final String ADMIN = "admin";
}
