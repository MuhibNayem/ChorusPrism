package com.chorus.observe.service;

import com.chorus.observe.model.Agent;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.Run;
import com.chorus.observe.persistence.AgentRepository;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.persistence.RunRepository.RunQuery;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service layer for agent operations and metrics.
 */
public class AgentService {

    private final AgentRepository agentRepository;
    private final RunRepository runRepository;
    private final JdbcTemplate jdbc;

    public AgentService(@NonNull AgentRepository agentRepository,
                        @NonNull RunRepository runRepository,
                        @NonNull DataSource dataSource) {
        this.agentRepository = Objects.requireNonNull(agentRepository);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    public @NonNull List<Agent> listAgents() {
        return agentRepository.findAll();
    }

    /**
     * Batch query for 24-hour metrics across ALL agents in one round-trip.
     * Returns a map keyed by agent_id.
     */
    public @NonNull Map<String, AgentMetricsSummary> getAll24hMetrics() {
        String sql = """
            SELECT agent_id,
                   COUNT(*) AS runs24h,
                   COALESCE(SUM(total_cost), 0) AS cost24h,
                   COALESCE(SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END), 0) AS errors24h,
                   COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0)::bigint AS latency_p95,
                   COALESCE(SUM(CASE WHEN status = 'ERROR' THEN 1.0 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) AS error_rate
            FROM runs
            WHERE start_time >= NOW() - INTERVAL '24 hours'
            GROUP BY agent_id
            """;
        Map<String, AgentMetricsSummary> result = new HashMap<>();
        jdbc.query(sql, (rs, rowNum) -> {
            String agentId = rs.getString("agent_id");
            AgentMetricsSummary summary = new AgentMetricsSummary(
                rs.getLong("runs24h"),
                rs.getDouble("cost24h"),
                rs.getLong("errors24h"),
                rs.getLong("latency_p95"),
                rs.getDouble("error_rate")
            );
            result.put(agentId, summary);
            return summary;
        });
        return result;
    }

    public @NonNull Optional<Agent> getAgent(@NonNull String agentId) {
        return agentRepository.findById(agentId);
    }

    public @NonNull AgentWithMetrics getAgentWithMetrics(@NonNull String agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        long runs24h = queryLong(
            "SELECT COUNT(*) FROM runs WHERE agent_id = ? AND start_time >= NOW() - INTERVAL '24 hours'",
            agentId);

        double cost24h = queryDouble(
            "SELECT COALESCE(SUM(total_cost), 0) FROM runs WHERE agent_id = ? AND start_time >= NOW() - INTERVAL '24 hours'",
            agentId);

        long errors24h = queryLong(
            "SELECT COUNT(*) FROM runs WHERE agent_id = ? AND start_time >= NOW() - INTERVAL '24 hours' AND status = 'ERROR'",
            agentId);

        Long latencyP95 = jdbc.queryForObject(
            """
                SELECT COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0)::bigint
                FROM runs WHERE agent_id = ? AND start_time >= NOW() - INTERVAL '24 hours'
                """,
            Long.class, agentId);

        List<Integer> runs24hSpark = queryHourlySpark(
            "SELECT COALESCE(COUNT(r.run_id), 0)::int FROM hours h LEFT JOIN runs r ON date_trunc('hour', r.start_time) = h.hour AND r.agent_id = ? GROUP BY h.hour ORDER BY h.hour",
            agentId);
        List<Integer> latencySpark = queryHourlySpark(
            "SELECT COALESCE(AVG(r.latency_ms), 0)::int FROM hours h LEFT JOIN runs r ON date_trunc('hour', r.start_time) = h.hour AND r.agent_id = ? GROUP BY h.hour ORDER BY h.hour",
            agentId);
        List<Integer> costSpark = queryHourlySpark(
            "SELECT COALESCE(SUM(r.total_cost), 0)::int FROM hours h LEFT JOIN runs r ON date_trunc('hour', r.start_time) = h.hour AND r.agent_id = ? GROUP BY h.hour ORDER BY h.hour",
            agentId);
        List<Integer> errorSpark = queryHourlySpark(
            "SELECT COALESCE(SUM(CASE WHEN r.status = 'ERROR' THEN 1 ELSE 0 END), 0)::int FROM hours h LEFT JOIN runs r ON date_trunc('hour', r.start_time) = h.hour AND r.agent_id = ? GROUP BY h.hour ORDER BY h.hour",
            agentId);

        return new AgentWithMetrics(
            agent,
            runs24h,
            cost24h,
            errors24h,
            latencyP95 != null ? latencyP95 : 0L,
            runs24hSpark,
            latencySpark,
            costSpark,
            errorSpark
        );
    }

    public @NonNull PagedResult<Run> getAgentRuns(@NonNull String agentId, int page, int size) {
        int offset = page * size;
        RunQuery query = new RunQuery(
            null, null, agentId, null, null, null, null,
            null, null, null, "start_time", "DESC", size, offset);
        List<Run> runs = runRepository.findAll(query);
        long total = runRepository.count(query);
        return new PagedResult<>(runs, total, page, size);
    }

    public @NonNull List<AgentToolUsage> getAgentTools(@NonNull String agentId) {
        String sql = """
            SELECT
                t.tool_name,
                COUNT(*) AS calls,
                COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY t.latency_ms), 0)::bigint AS p95,
                COALESCE(SUM(CASE WHEN t.error IS NOT NULL THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) AS err_rate
            FROM tool_calls t
            JOIN runs r ON t.run_id = r.run_id
            WHERE r.agent_id = ? AND r.start_time >= NOW() - INTERVAL '24 hours'
            GROUP BY t.tool_name
            ORDER BY calls DESC
            """;
        return jdbc.query(sql, (rs, rowNum) -> new AgentToolUsage(
            rs.getString("tool_name"),
            rs.getLong("calls"),
            rs.getLong("p95"),
            rs.getDouble("err_rate")
        ), agentId);
    }

    public @NonNull List<AgentModelDistribution> getAgentModels(@NonNull String agentId) {
        String sql = """
            SELECT
                l.model,
                l.provider,
                COUNT(*) AS calls,
                COALESCE(SUM(l.cost_usd), 0) AS cost
            FROM llm_calls l
            JOIN runs r ON l.run_id = r.run_id
            WHERE r.agent_id = ? AND r.start_time >= NOW() - INTERVAL '24 hours'
            GROUP BY l.model, l.provider
            ORDER BY calls DESC
            """;
        record RawModel(String model, String provider, long calls, double cost) {}

        List<RawModel> rows = jdbc.query(sql, (rs, rowNum) -> {
            String model = rs.getString("model");
            String provider = rs.getString("provider");
            return new RawModel(
                model,
                inferProvider(model, provider),
                rs.getLong("calls"),
                rs.getDouble("cost")
            );
        }, agentId);

        long totalCalls = rows.stream().mapToLong(RawModel::calls).sum();
        if (totalCalls == 0) {
            return List.of();
        }

        List<AgentModelDistribution> result = new ArrayList<>();
        for (RawModel row : rows) {
            int pct = (int) Math.round(row.calls() * 100.0 / totalCalls);
            result.add(new AgentModelDistribution(row.model(), row.provider(), pct, row.cost()));
        }
        return result;
    }

    public @NonNull Agent registerAgent(
            @NonNull String agentId,
            @NonNull String name,
            @Nullable String description,
            @Nullable String framework,
            @Nullable String runtime,
            @Nullable String owner,
            @Nullable String ownerEmail,
            @Nullable List<String> tags,
            @Nullable String version,
            @Nullable String repo,
            @Nullable String branch) {
        if (agentRepository.exists(agentId)) {
            throw new IllegalArgumentException("Agent already exists: " + agentId);
        }
        Agent agent = new Agent(
            agentId,
            name,
            description,
            framework,
            runtime,
            owner,
            ownerEmail,
            tags != null ? tags : List.of(),
            version,
            Instant.now(),
            null,
            Agent.Status.healthy,
            null,
            repo,
            branch
        );
        agentRepository.save(agent);
        return agent;
    }

    public @NonNull Agent updateAgent(
            @NonNull String agentId,
            @Nullable String name,
            @Nullable String description,
            @Nullable String framework,
            @Nullable String runtime,
            @Nullable String owner,
            @Nullable String ownerEmail,
            @Nullable List<String> tags,
            @Nullable String version,
            @Nullable Instant deployedAt,
            @Nullable String deployedBy,
            Agent.@Nullable Status status,
            @Nullable Double health,
            @Nullable String repo,
            @Nullable String branch) {
        Agent existing = agentRepository.findById(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        Agent updated = new Agent(
            agentId,
            name != null ? name : existing.name(),
            description != null ? description : existing.description(),
            framework != null ? framework : existing.framework(),
            runtime != null ? runtime : existing.runtime(),
            owner != null ? owner : existing.owner(),
            ownerEmail != null ? ownerEmail : existing.ownerEmail(),
            tags != null ? tags : existing.tags(),
            version != null ? version : existing.version(),
            deployedAt != null ? deployedAt : existing.deployedAt(),
            deployedBy != null ? deployedBy : existing.deployedBy(),
            status != null ? status : existing.status(),
            health != null ? health : existing.health(),
            repo != null ? repo : existing.repo(),
            branch != null ? branch : existing.branch()
        );
        agentRepository.save(updated);
        return updated;
    }

    public void deleteAgent(@NonNull String agentId) {
        agentRepository.deleteById(agentId);
    }

    private long queryLong(@NonNull String sql, @NonNull String agentId) {
        Long value = jdbc.queryForObject(sql, Long.class, agentId);
        return value != null ? value : 0L;
    }

    private double queryDouble(@NonNull String sql, @NonNull String agentId) {
        Double value = jdbc.queryForObject(sql, Double.class, agentId);
        return value != null ? value : 0.0;
    }

    private @NonNull List<Integer> queryHourlySpark(@NonNull String joinSql, @NonNull String agentId) {
        String sql = """
            WITH hours AS (
                SELECT generate_series(
                    date_trunc('hour', NOW() - INTERVAL '23 hours'),
                    date_trunc('hour', NOW()),
                    INTERVAL '1 hour'
                ) AS hour
            )
            """ + joinSql;
        return jdbc.query(sql, (rs, rowNum) -> rs.getInt(1), agentId);
    }

    private static @NonNull String inferProvider(@NonNull String model, @Nullable String storedProvider) {
        if (storedProvider != null && !storedProvider.isBlank() && !"unknown".equalsIgnoreCase(storedProvider)) {
            return storedProvider;
        }
        return DashboardService.inferProvider(model);
    }

    public record AgentWithMetrics(
        @NonNull Agent agent,
        long runs24h,
        double cost24h,
        long errors24h,
        long latencyP95,
        @NonNull List<Integer> runs24hSpark,
        @NonNull List<Integer> latencySpark,
        @NonNull List<Integer> costSpark,
        @NonNull List<Integer> errorSpark
    ) {
        public AgentWithMetrics {
            Objects.requireNonNull(agent, "agent");
            runs24hSpark = runs24hSpark != null ? List.copyOf(runs24hSpark) : List.of();
            latencySpark = latencySpark != null ? List.copyOf(latencySpark) : List.of();
            costSpark = costSpark != null ? List.copyOf(costSpark) : List.of();
            errorSpark = errorSpark != null ? List.copyOf(errorSpark) : List.of();
        }
    }

    public record AgentToolUsage(
        @NonNull String name,
        long calls,
        long p95,
        double errRate
    ) {
        public AgentToolUsage {
            Objects.requireNonNull(name, "name");
        }
    }

    public record AgentModelDistribution(
        @NonNull String model,
        @NonNull String provider,
        int pct,
        double cost
    ) {
        public AgentModelDistribution {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(provider, "provider");
        }
    }

    /**
     * Lightweight 24-hour metrics summary for a single agent, produced by the batch query.
     */
    public record AgentMetricsSummary(
        long runs24h,
        double cost24h,
        long errors24h,
        long latencyP95,
        double errorRate
    ) {
        public static AgentMetricsSummary empty() {
            return new AgentMetricsSummary(0L, 0.0, 0L, 0L, 0.0);
        }
    }
}
