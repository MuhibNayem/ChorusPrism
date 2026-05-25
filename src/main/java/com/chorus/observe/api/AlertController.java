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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    public ResponseEntity<PagedResult<AlertEventResponse>> listRecentEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResult<AlertEvent> raw = alertService.getRecentEvents(page, size);
        List<AlertEventResponse> responses = raw.items().stream()
            .map(e -> toResponse(e, alertService.getRule(e.ruleId()).orElse(null)))
            .toList();
        return ResponseEntity.ok(new PagedResult<>(responses, raw.total(), raw.page(), raw.size()));
    }

    @GetMapping("/events/unresolved")
    public ResponseEntity<PagedResult<AlertEventResponse>> listUnresolvedEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResult<AlertEvent> raw = alertService.getUnresolvedEvents(page, size);
        List<AlertEventResponse> responses = raw.items().stream()
            .map(e -> toResponse(e, alertService.getRule(e.ruleId()).orElse(null)))
            .toList();
        return ResponseEntity.ok(new PagedResult<>(responses, raw.total(), raw.page(), raw.size()));
    }

    @PostMapping("/events/{eventId}/resolve")
    public ResponseEntity<Void> resolveEvent(@PathVariable @NonNull String eventId) {
        alertService.resolveEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    private static AlertEventResponse toResponse(AlertEvent event, AlertRule rule) {
        String severityStr = rule != null ? rule.severity().name() : "warning";
        String title = rule != null ? rule.name() : "Alert triggered";
        String sub = rule != null
            ? "Threshold " + rule.threshold() + " exceeded (value=" + event.value() + ")"
            : "Rule threshold exceeded: " + event.ruleId();
        String evt = event.resolvedAt() != null ? "resolved" : "firing";
        String when = relativeTime(event.triggeredAt());
        return new AlertEventResponse(event.eventId(), event.ruleId(), severityStr, title, sub, evt, when);
    }

    private static String relativeTime(Instant ts) {
        long mins = ChronoUnit.MINUTES.between(ts, Instant.now());
        if (mins < 2) return "just now";
        if (mins < 60) return mins + "m ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + "h ago";
        return (hrs / 24) + "d ago";
    }

    public record CreateRuleRequest(@NotBlank String name, @NotBlank String conditionExpr, double threshold, @NotNull AlertRule.Severity severity, String webhookUrl, String email, int cooldownSeconds) {}
    public record TriggerEventRequest(double value, @NotNull Map<String, Object> metadata) {}

    public record AlertEventResponse(
        String eventId,
        String ruleId,
        String severity,
        String title,
        String sub,
        String evt,
        String when
    ) {}
}
