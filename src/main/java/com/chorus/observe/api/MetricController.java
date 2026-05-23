package com.chorus.observe.api;

import com.chorus.observe.model.MetricSnapshot;
import com.chorus.observe.persistence.MetricRepository.MetricAggregate;
import com.chorus.observe.service.DashboardService;
import com.chorus.observe.service.MetricService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * REST API v1 for metrics and dashboards.
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {

    private final MetricService metricService;
    private final DashboardService dashboardService;

    public MetricController(@NonNull MetricService metricService, @NonNull DashboardService dashboardService) {
        this.metricService = Objects.requireNonNull(metricService);
        this.dashboardService = Objects.requireNonNull(dashboardService);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardService.DashboardMetrics> getDashboard(
            @RequestParam(defaultValue = "24h") String window) {
        return ResponseEntity.ok(dashboardService.getMetrics(window));
    }

    @GetMapping("/heatmap")
    public ResponseEntity<List<List<Integer>>> getHeatmap(
            @RequestParam(defaultValue = "7d") String window) {
        return ResponseEntity.ok(dashboardService.getHeatmap(window));
    }

    @GetMapping("/cost")
    public ResponseEntity<List<MetricSnapshot>> getCostMetrics(
            @RequestParam @NonNull Instant from,
            @RequestParam @NonNull Instant to,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(metricService.getMetrics("cost_usd", from, to, limit));
    }

    @GetMapping("/latency")
    public ResponseEntity<List<MetricSnapshot>> getLatencyMetrics(
            @RequestParam @NonNull Instant from,
            @RequestParam @NonNull Instant to,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(metricService.getMetrics("latency_ms", from, to, limit));
    }

    @GetMapping("/tokens")
    public ResponseEntity<List<MetricSnapshot>> getTokenMetrics(
            @RequestParam @NonNull Instant from,
            @RequestParam @NonNull Instant to,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(metricService.getMetrics("total_tokens", from, to, limit));
    }

    @GetMapping("/aggregate")
    public ResponseEntity<List<MetricAggregate>> getAggregatedMetrics(
            @RequestParam @NonNull String metricName,
            @RequestParam @NonNull Instant from,
            @RequestParam @NonNull Instant to) {
        return ResponseEntity.ok(metricService.aggregateByHour(metricName, from, to));
    }
}
