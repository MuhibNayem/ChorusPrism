package com.chorus.observe.service;

import com.chorus.observe.model.Run;
import com.chorus.observe.persistence.RunRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates dashboard metrics from run and span data.
 */
public class DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardService(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    public @NonNull DashboardMetrics getMetrics() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        // Overall stats
        var overall = jdbc.queryForMap(
            "SELECT COUNT(*) as total_runs, COALESCE(SUM(total_tokens), 0) as total_tokens, " +
            "COALESCE(SUM(total_cost), 0) as total_cost, COALESCE(AVG(latency_ms), 0) as avg_latency " +
            "FROM runs WHERE start_time >= ?",
            java.sql.Timestamp.from(sevenDaysAgo)
        );

        // Runs by day
        List<DayMetrics> runsByDay = jdbc.query(
            "SELECT DATE(start_time) as day, COUNT(*) as count, COALESCE(SUM(total_tokens), 0) as tokens, " +
            "COALESCE(SUM(total_cost), 0) as cost FROM runs WHERE start_time >= ? " +
            "GROUP BY DATE(start_time) ORDER BY day ASC",
            (rs, rowNum) -> new DayMetrics(
                rs.getDate("day").toLocalDate().toString(),
                rs.getLong("count"),
                rs.getLong("tokens"),
                rs.getBigDecimal("cost").doubleValue()
            ),
            java.sql.Timestamp.from(sevenDaysAgo)
        );

        // Top models
        List<ModelMetrics> topModels = jdbc.query(
            "SELECT model, COUNT(*) as calls, COALESCE(SUM(total_tokens), 0) as tokens, " +
            "COALESCE(SUM(total_cost), 0) as cost FROM runs WHERE start_time >= ? AND model IS NOT NULL " +
            "GROUP BY model ORDER BY calls DESC LIMIT 5",
            (rs, rowNum) -> new ModelMetrics(
                rs.getString("model"),
                rs.getLong("calls"),
                rs.getLong("tokens"),
                rs.getBigDecimal("cost").doubleValue()
            ),
            java.sql.Timestamp.from(sevenDaysAgo)
        );

        // Top agents
        List<AgentMetrics> topAgents = jdbc.query(
            "SELECT agent_id, COUNT(*) as runs, COALESCE(SUM(total_tokens), 0) as tokens, " +
            "COALESCE(SUM(total_cost), 0) as cost FROM runs WHERE start_time >= ? " +
            "GROUP BY agent_id ORDER BY runs DESC LIMIT 5",
            (rs, rowNum) -> new AgentMetrics(
                rs.getString("agent_id"),
                rs.getLong("runs"),
                rs.getLong("tokens"),
                rs.getBigDecimal("cost").doubleValue()
            ),
            java.sql.Timestamp.from(sevenDaysAgo)
        );

        // Status breakdown
        List<StatusMetrics> statusBreakdown = jdbc.query(
            "SELECT status, COUNT(*) as count FROM runs WHERE start_time >= ? GROUP BY status",
            (rs, rowNum) -> new StatusMetrics(
                rs.getString("status"),
                rs.getLong("count")
            ),
            java.sql.Timestamp.from(sevenDaysAgo)
        );

        return new DashboardMetrics(
            ((Number) overall.get("total_runs")).longValue(),
            ((Number) overall.get("total_tokens")).longValue(),
            ((Number) overall.get("total_cost")).doubleValue(),
            ((Number) overall.get("avg_latency")).doubleValue(),
            runsByDay,
            topModels,
            topAgents,
            statusBreakdown
        );
    }

    public record DashboardMetrics(
        long totalRuns,
        long totalTokens,
        double totalCost,
        double avgLatencyMs,
        List<DayMetrics> runsByDay,
        List<ModelMetrics> topModels,
        List<AgentMetrics> topAgents,
        List<StatusMetrics> statusBreakdown
    ) {}

    public record DayMetrics(String date, long count, long tokens, double cost) {}
    public record ModelMetrics(String model, long calls, long tokens, double cost) {}
    public record AgentMetrics(String agentId, long runs, long tokens, double cost) {}
    public record StatusMetrics(String status, long count) {}
}
