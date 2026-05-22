package com.chorus.observe.dashboard;

import com.chorus.observe.model.Dashboard;
import com.chorus.observe.model.DashboardWidget;
import com.chorus.observe.persistence.DashboardRepository;
import com.chorus.observe.persistence.DashboardWidgetRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CustomDashboardService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomDashboardService.class);

    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final JdbcTemplate jdbc;

    public CustomDashboardService(@NonNull DashboardRepository dashboardRepository,
                                  @NonNull DashboardWidgetRepository dashboardWidgetRepository,
                                  @NonNull DataSource dataSource) {
        this.dashboardRepository = Objects.requireNonNull(dashboardRepository);
        this.dashboardWidgetRepository = Objects.requireNonNull(dashboardWidgetRepository);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public @NonNull Dashboard createDashboard(@NonNull String tenantId, @NonNull String userId,
                                               @NonNull String name, @NonNull String description,
                                               @NonNull Map<String, Object> layout) {
        String dashboardId = "dash-" + UUID.randomUUID().toString().substring(0, 8);
        Dashboard dashboard = new Dashboard(dashboardId, tenantId, userId, name, description, layout, true,
            Instant.now(), Instant.now());
        dashboardRepository.save(dashboard);
        return dashboard;
    }

    public @NonNull DashboardWidget createWidget(@NonNull String dashboardId, DashboardWidget.WidgetType type,
                                                  @NonNull String title, @NonNull Map<String, Object> queryConfig,
                                                  @NonNull Map<String, Object> position, int refreshSeconds) {
        String widgetId = "wid-" + UUID.randomUUID().toString().substring(0, 8);
        DashboardWidget widget = new DashboardWidget(widgetId, dashboardId, type, title, queryConfig, position,
            refreshSeconds, Instant.now(), Instant.now());
        dashboardWidgetRepository.save(widget);
        return widget;
    }

    public @NonNull Optional<Dashboard> getDashboard(@NonNull String dashboardId) {
        return dashboardRepository.findById(dashboardId);
    }

    public @NonNull List<Dashboard> listDashboardsByTenant(@NonNull String tenantId) {
        return dashboardRepository.findByTenant(tenantId);
    }

    public @NonNull List<DashboardWidget> listWidgets(@NonNull String dashboardId) {
        return dashboardWidgetRepository.findByDashboardId(dashboardId);
    }

    public @NonNull WidgetResult executeWidgetQuery(@NonNull DashboardWidget widget) {
        String sql = Objects.toString(widget.queryConfig().get("sql"), null);
        if (sql == null || sql.isBlank()) {
            return new WidgetResult(List.of(), "Missing SQL query in widget config");
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            return new WidgetResult(rows, null);
        } catch (Exception e) {
            LOG.error("Widget query failed for widget {}: {}", widget.widgetId(), sql, e);
            return new WidgetResult(List.of(), e.getMessage());
        }
    }

    public void deleteDashboard(@NonNull String dashboardId) {
        dashboardWidgetRepository.deleteByDashboardId(dashboardId);
        dashboardRepository.deleteById(dashboardId);
    }

    public record WidgetResult(@NonNull List<Map<String, Object>> rows, @Nullable String error) {}
}
