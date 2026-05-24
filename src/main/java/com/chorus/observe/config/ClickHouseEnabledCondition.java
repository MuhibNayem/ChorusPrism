package com.chorus.observe.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition to dynamically enable ClickHouse resources only if the active span store requires it.
 * Prevents application startup crashes from name resolution or unreachable ClickHouse instances
 * when PostgreSQL is the sole active storage engine.
 */
public class ClickHouseEnabledCondition implements Condition {

    @Override
    public boolean matches(@NonNull ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        String spanStore = context.getEnvironment().getProperty("chorus.observe.storage.span-store", "postgresql");
        return "clickhouse".equalsIgnoreCase(spanStore) || "dual".equalsIgnoreCase(spanStore);
    }
}
