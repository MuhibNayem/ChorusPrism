package com.chorus.observe.api;

import com.chorus.observe.clustering.TraceClusteringEngine;
import com.chorus.observe.model.BudgetEnforcement;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.TraceCluster;
import com.chorus.observe.service.BudgetService;
import com.chorus.observe.service.TraceClusterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for production monitoring: clusters and budgets.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {

    private final TraceClusterService traceClusterService;
    private final BudgetService budgetService;

    public MonitoringController(@NonNull TraceClusterService traceClusterService, @NonNull BudgetService budgetService) {
        this.traceClusterService = Objects.requireNonNull(traceClusterService);
        this.budgetService = Objects.requireNonNull(budgetService);
    }

    @GetMapping("/clusters")
    public ResponseEntity<PagedResult<TraceCluster>> listClusters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(traceClusterService.listClusters(page, size));
    }

    @PostMapping("/clusters")
    public ResponseEntity<TraceCluster> createCluster(@RequestBody @Valid @NonNull CreateClusterRequest request) {
        TraceCluster cluster = traceClusterService.createCluster(request.label(), request.description(), request.runCount(), request.avgScore(), request.avgCost(), request.periodStart(), request.periodEnd(), request.metadata());
        return ResponseEntity.ok(cluster);
    }

    @GetMapping("/budgets")
    public ResponseEntity<PagedResult<BudgetEnforcement>> listBudgets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(budgetService.listBudgets(page, size));
    }

    @GetMapping("/budgets/{enforcementId}")
    public ResponseEntity<BudgetEnforcement> getBudget(@PathVariable @NonNull String enforcementId) {
        return budgetService.getBudget(enforcementId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/budgets")
    public ResponseEntity<BudgetEnforcement> createBudget(@RequestBody @Valid @NonNull CreateBudgetRequest request) {
        BudgetEnforcement budget = budgetService.createBudget(request.agentId(), request.budgetType(), request.limitValue(), request.currency());
        return ResponseEntity.ok(budget);
    }

    @PostMapping("/budgets/{enforcementId}/spend")
    public ResponseEntity<BudgetEnforcement> recordSpending(@PathVariable @NonNull String enforcementId, @RequestBody @Valid @NonNull RecordSpendingRequest request) {
        return ResponseEntity.ok(budgetService.updateSpending(enforcementId, request.amount()));
    }

    @PostMapping("/budgets/{enforcementId}/pause")
    public ResponseEntity<Void> pauseBudget(@PathVariable @NonNull String enforcementId) {
        budgetService.pauseBudget(enforcementId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/budgets/{enforcementId}/resume")
    public ResponseEntity<Void> resumeBudget(@PathVariable @NonNull String enforcementId) {
        budgetService.resumeBudget(enforcementId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clusters/analyze")
    public ResponseEntity<TraceClusteringEngine.ClusteringReport> analyzeClusters(@RequestBody @Valid @NonNull AnalyzeClustersRequest request) {
        TraceClusteringEngine.ClusteringReport report = traceClusterService.analyzeClusters(
            request.periodStart(), request.periodEnd(), request.model(), request.agentConfig(),
            request.minSimilarity(), request.minPoints());
        return ResponseEntity.ok(report);
    }

    public record CreateClusterRequest(@NotBlank String label, String description, int runCount, Double avgScore, BigDecimal avgCost, @NotNull Instant periodStart, @NotNull Instant periodEnd, @NotNull Map<String, Object> metadata) {}
    public record AnalyzeClustersRequest(@NotNull Instant periodStart, @NotNull Instant periodEnd, @NotBlank String model, @NotNull Map<String, Object> agentConfig, double minSimilarity, int minPoints) {}
    public record CreateBudgetRequest(@NotBlank String agentId, @NotBlank String budgetType, @NotNull BigDecimal limitValue, @NotBlank String currency) {}
    public record RecordSpendingRequest(@NotNull BigDecimal amount) {}
}
