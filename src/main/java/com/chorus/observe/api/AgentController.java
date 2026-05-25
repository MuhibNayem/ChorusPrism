package com.chorus.observe.api;

import com.chorus.observe.model.Agent;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.Run;
import com.chorus.observe.service.AgentService;
import com.chorus.observe.service.AgentService.AgentMetricsSummary;
import com.chorus.observe.service.AgentService.AgentModelDistribution;
import com.chorus.observe.service.AgentService.AgentToolUsage;
import com.chorus.observe.service.AgentService.AgentWithMetrics;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for agents.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(@NonNull AgentService agentService) {
        this.agentService = Objects.requireNonNull(agentService);
    }

    @GetMapping
    public ResponseEntity<List<AgentResponse>> listAgents() {
        List<Agent> agents = agentService.listAgents();
        Map<String, AgentMetricsSummary> metricsMap = agentService.getAll24hMetrics();
        List<AgentResponse> responses = agents.stream()
            .map(a -> {
                AgentMetricsSummary m = metricsMap.getOrDefault(a.agentId(), AgentMetricsSummary.empty());
                return new AgentResponse(a, m.runs24h(), m.latencyP95(), null, null, m.cost24h(), m.errors24h(), m.errorRate());
            })
            .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentResponse> getAgent(@PathVariable @NonNull String agentId) {
        return agentService.getAgent(agentId)
            .map(a -> {
                AgentWithMetrics m = agentService.getAgentWithMetrics(agentId);
                return new AgentResponse(a, m.runs24h(), m.latencyP95(), null, null, m.cost24h(), m.errors24h(),
                    m.runs24h() > 0 ? m.errors24h() * 100.0 / m.runs24h() : 0.0);
            })
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{agentId}/runs")
    public ResponseEntity<PagedResult<Run>> getAgentRuns(
            @PathVariable @NonNull String agentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(agentService.getAgentRuns(agentId, page, size));
    }

    @GetMapping("/{agentId}/metrics")
    public ResponseEntity<SparklineMetrics> getAgentMetrics(@PathVariable @NonNull String agentId) {
        try {
            AgentWithMetrics m = agentService.getAgentWithMetrics(agentId);
            return ResponseEntity.ok(new SparklineMetrics(
                m.runs24hSpark(),
                m.latencySpark(),
                m.costSpark(),
                m.errorSpark()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{agentId}/tools")
    public ResponseEntity<List<AgentToolUsage>> getAgentTools(@PathVariable @NonNull String agentId) {
        return ResponseEntity.ok(agentService.getAgentTools(agentId));
    }

    @GetMapping("/{agentId}/models")
    public ResponseEntity<List<AgentModelDistribution>> getAgentModels(@PathVariable @NonNull String agentId) {
        return ResponseEntity.ok(agentService.getAgentModels(agentId));
    }

    @GetMapping("/{agentId}/alerts")
    public ResponseEntity<List<Object>> getAgentAlerts(@PathVariable @NonNull String agentId) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{agentId}/deployments")
    public ResponseEntity<List<Object>> getAgentDeployments(@PathVariable @NonNull String agentId) {
        return ResponseEntity.ok(List.of());
    }

    @PostMapping
    public ResponseEntity<Agent> registerAgent(@RequestBody @Valid @NonNull RegisterRequest request) {
        Agent agent = agentService.registerAgent(
            request.agentId(),
            request.name(),
            request.description(),
            request.framework(),
            request.runtime(),
            request.owner(),
            request.ownerEmail(),
            request.tags(),
            request.version(),
            request.repo(),
            request.branch()
        );
        return ResponseEntity.ok(agent);
    }

    @PatchMapping("/{agentId}")
    public ResponseEntity<Agent> updateAgent(
            @PathVariable @NonNull String agentId,
            @RequestBody @NonNull UpdateRequest request) {
        try {
            Agent agent = agentService.updateAgent(
                agentId,
                request.name(),
                request.description(),
                request.framework(),
                request.runtime(),
                request.owner(),
                request.ownerEmail(),
                request.tags(),
                request.version(),
                request.deployedAt(),
                request.deployedBy(),
                request.status(),
                request.health(),
                request.repo(),
                request.branch()
            );
            return ResponseEntity.ok(agent);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteAgent(@PathVariable @NonNull String agentId) {
        agentService.deleteAgent(agentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Agent response DTO that includes all Agent fields plus 24h metrics.
     */
    public record AgentResponse(
        String agentId,
        String name,
        String description,
        String framework,
        String runtime,
        String owner,
        String ownerEmail,
        List<String> tags,
        String version,
        Instant deployedAt,
        String deployedBy,
        Agent.Status status,
        Double health,
        String repo,
        String branch,
        long runs24h,
        long latencyP95,
        Long latencyP50,
        Long latencyP99,
        double cost24h,
        long errors24h,
        double errorRate
    ) {
        AgentResponse(Agent a, long runs24h, long latencyP95, Long latencyP50, Long latencyP99,
                      double cost24h, long errors24h, double errorRate) {
            this(
                a.agentId(), a.name(), a.description(), a.framework(), a.runtime(),
                a.owner(), a.ownerEmail(), a.tags(), a.version(),
                a.deployedAt(), a.deployedBy(), a.status(), a.health(), a.repo(), a.branch(),
                runs24h, latencyP95, latencyP50, latencyP99, cost24h, errors24h, errorRate
            );
        }
    }

    /**
     * Sparkline-only metrics for the agent detail page charts.
     * Maps to the frontend {@code AgentMetrics} interface.
     */
    public record SparklineMetrics(
        List<Integer> runs24hSpark,
        List<Integer> latencySpark,
        List<Integer> costSpark,
        List<Integer> errorSpark
    ) {}

    public record RegisterRequest(
        @NotBlank String agentId,
        @NotBlank String name,
        String description,
        String framework,
        String runtime,
        String owner,
        String ownerEmail,
        List<String> tags,
        String version,
        String repo,
        String branch
    ) {}

    public record UpdateRequest(
        String name,
        String description,
        String framework,
        String runtime,
        String owner,
        String ownerEmail,
        List<String> tags,
        String version,
        Instant deployedAt,
        String deployedBy,
        Agent.Status status,
        Double health,
        String repo,
        String branch
    ) {}
}
