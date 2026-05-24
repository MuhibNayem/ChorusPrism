package com.chorus.observe.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a permission required to invoke the annotated controller method.
 * The permission is enforced by {@link PermissionInterceptor}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /**
     * Permission string, e.g. "runs:read" or "admin".
     */
    String value();
}
