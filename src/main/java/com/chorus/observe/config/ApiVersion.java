package com.chorus.observe.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a controller or method as supporting a specific API version.
 * If no value is specified, all versions are accepted.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiVersion {
    /**
     * Supported API versions. Empty means all versions are accepted.
     */
    String[] value() default {};
}
