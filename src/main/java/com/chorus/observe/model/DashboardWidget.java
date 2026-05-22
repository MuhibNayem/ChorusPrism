package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record DashboardWidget(
    @NonNull String widgetId,
    @NonNull String dashboardId,
    @NonNull WidgetType widgetType,
    @NonNull String title,
    @NonNull Map<String, Object> queryConfig,
    @NonNull Map<String, Object> position,
    int refreshSeconds,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public DashboardWidget {
        Objects.requireNonNull(widgetId);
        Objects.requireNonNull(dashboardId);
        Objects.requireNonNull(widgetType);
        Objects.requireNonNull(title);
        Objects.requireNonNull(queryConfig);
        Objects.requireNonNull(position);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public enum WidgetType { LINE_CHART, BAR_CHART, STAT_CARD, TABLE, PIE_CHART }
}
