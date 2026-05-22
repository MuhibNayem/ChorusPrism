package com.chorus.observe.api;

import com.chorus.observe.model.AlertEvent;
import com.chorus.observe.model.AlertRule;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.service.AlertService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for alert rules and events.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(@NonNull AlertService alertService) {
        this.alertService = Objects.requireNonNull(alertService);
    }

    @PostMapping("/rules")
    public ResponseEntity<AlertRule> createRule(@RequestBody @Valid @NonNull CreateRuleRequest request) {
        AlertRule rule = alertService.createRule(request.name(), request.conditionExpr(), request.threshold(), request.severity(), request.webhookUrl(), request.email(), request.cooldownSeconds());
        return ResponseEntity.ok(rule);
    }

    @GetMapping("/rules")
    public ResponseEntity<PagedResult<AlertRule>> listRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.listRules(page, size));
    }

    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<AlertRule> getRule(@PathVariable @NonNull String ruleId) {
        return alertService.getRule(ruleId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable @NonNull String ruleId) {
        alertService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rules/{ruleId}/trigger")
    public ResponseEntity<AlertEvent> triggerEvent(@PathVariable @NonNull String ruleId, @RequestBody @Valid @NonNull TriggerEventRequest request) {
        AlertEvent event = alertService.triggerEvent(ruleId, request.value(), request.metadata());
        return ResponseEntity.ok(event);
    }

    @GetMapping("/events")
    public ResponseEntity<PagedResult<AlertEvent>> listRecentEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.getRecentEvents(page, size));
    }

    @GetMapping("/events/unresolved")
    public ResponseEntity<PagedResult<AlertEvent>> listUnresolvedEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.getUnresolvedEvents(page, size));
    }

    @PostMapping("/events/{eventId}/resolve")
    public ResponseEntity<Void> resolveEvent(@PathVariable @NonNull String eventId) {
        alertService.resolveEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    public record CreateRuleRequest(@NotBlank String name, @NotBlank String conditionExpr, double threshold, @NotNull AlertRule.Severity severity, String webhookUrl, String email, int cooldownSeconds) {}
    public record TriggerEventRequest(double value, @NotNull Map<String, Object> metadata) {}
}
