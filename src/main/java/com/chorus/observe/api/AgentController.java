package com.chorus.observe.api;

import com.chorus.observe.model.Agent;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.Run;
import com.chorus.observe.service.AgentService;
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
    public ResponseEntity<List<Agent>> listAgents() {
        return ResponseEntity.ok(agentService.listAgents());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<Agent> getAgent(@PathVariable @NonNull String agentId) {
        return agentService.getAgent(agentId)
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
    public ResponseEntity<AgentWithMetrics> getAgentMetrics(@PathVariable @NonNull String agentId) {
        try {
            return ResponseEntity.ok(agentService.getAgentWithMetrics(agentId));
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
