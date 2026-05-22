package com.chorus.observe.api;

import com.chorus.observe.dashboard.CustomDashboardService;
import com.chorus.observe.model.Dashboard;
import com.chorus.observe.model.DashboardWidget;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private final CustomDashboardService dashboardService;

    public DashboardController(@NonNull CustomDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostMapping
    public ResponseEntity<?> createDashboard(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();
        if (userId == null) userId = "system";
        String name = (String) request.get("name");
        String description = (String) request.getOrDefault("description", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> layout = (Map<String, Object>) request.getOrDefault("layout", Map.of());
        Dashboard dashboard = dashboardService.createDashboard(tenantId, userId, name, description, layout);
        return ResponseEntity.ok(Map.of("dashboardId", dashboard.dashboardId()));
    }

    @GetMapping
    public ResponseEntity<List<Dashboard>> listDashboards() {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(dashboardService.listDashboardsByTenant(tenantId));
    }

    @GetMapping("/{dashboardId}")
    public ResponseEntity<?> getDashboard(@PathVariable String dashboardId) {
        return dashboardService.getDashboard(dashboardId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{dashboardId}/widgets")
    public ResponseEntity<?> createWidget(@PathVariable String dashboardId, @RequestBody Map<String, Object> request) {
        String type = (String) request.get("widgetType");
        String title = (String) request.get("title");
        @SuppressWarnings("unchecked")
        Map<String, Object> queryConfig = (Map<String, Object>) request.getOrDefault("queryConfig", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> position = (Map<String, Object>) request.getOrDefault("position", Map.of());
        int refreshSeconds = (int) request.getOrDefault("refreshSeconds", 60);
        DashboardWidget widget = dashboardService.createWidget(dashboardId,
            DashboardWidget.WidgetType.valueOf(type), title, queryConfig, position, refreshSeconds);
        return ResponseEntity.ok(Map.of("widgetId", widget.widgetId()));
    }

    @GetMapping("/{dashboardId}/widgets")
    public ResponseEntity<List<DashboardWidget>> listWidgets(@PathVariable String dashboardId) {
        return ResponseEntity.ok(dashboardService.listWidgets(dashboardId));
    }

    @PostMapping("/widgets/{widgetId}/execute")
    public ResponseEntity<?> executeWidget(@PathVariable String widgetId) {
        return dashboardService.getDashboard(widgetId)
            .flatMap(d -> dashboardService.listWidgets(d.dashboardId()).stream()
                .filter(w -> w.widgetId().equals(widgetId))
                .findFirst())
            .map(dashboardService::executeWidgetQuery)
            .map(result -> ResponseEntity.ok(Map.of(
                "rows", result.rows(),
                "error", result.error() != null ? result.error() : "")))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{dashboardId}")
    public ResponseEntity<?> deleteDashboard(@PathVariable String dashboardId) {
        dashboardService.deleteDashboard(dashboardId);
        return ResponseEntity.noContent().build();
    }
}
