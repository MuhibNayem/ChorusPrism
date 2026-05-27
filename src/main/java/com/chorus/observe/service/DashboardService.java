package com.chorus.observe.service;

import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates dashboard metrics from run and span data.
 */
public class DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardService(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    public @NonNull DashboardMetrics getMetrics(@NonNull String window) {
        Instant now = Instant.now();
        long windowHours = parseWindow(window);
        Instant windowStart = now.minus(windowHours, ChronoUnit.HOURS);
        Instant previousWindowStart = now.minus(windowHours * 2, ChronoUnit.HOURS);

        String tenantId = TenantContext.getTenantIdOrNull();

        Map<String, Object> currentOverall;
        Map<String, Object> previousOverall;
        List<Integer> runsSpark = new ArrayList<>(Collections.nCopies(24, 0));
        List<Integer> tokensSpark = new ArrayList<>(Collections.nCopies(24, 0));
        List<Integer> costSpark = new ArrayList<>(Collections.nCopies(24, 0));
        List<Integer> latencySpark = new ArrayList<>(Collections.nCopies(24, 0));
        List<DayMetrics> runsByDay;
        List<ModelMetrics> topModels;
        List<AgentMetrics> topAgents;
        List<StatusMetrics> statusBreakdown;

        long windowSeconds = windowHours * 3600L;
        long bucketSeconds = Math.max(1, windowSeconds / 24);

        if (tenantId != null) {
            // Current window overall stats (filtered by tenant)
            currentOverall = jdbc.queryForMap(
                """
                SELECT COUNT(*) as total_runs,
                       COALESCE(SUM(total_tokens), 0) as total_tokens,
                       COALESCE(SUM(total_cost), 0) as total_cost,
                       COALESCE(AVG(latency_ms), 0) as avg_latency,
                       COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) as p95_latency,
                       COALESCE(SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END), 0) as error_count
                FROM runs
                WHERE start_time >= ? AND start_time < ? AND tenant_id = ?
                """,
                Timestamp.from(windowStart), Timestamp.from(now), tenantId
            );

            // Previous window overall stats (filtered by tenant)
            previousOverall = jdbc.queryForMap(
                """
                SELECT COUNT(*) as total_runs,
                       COALESCE(SUM(total_tokens), 0) as total_tokens,
                       COALESCE(SUM(total_cost), 0) as total_cost,
                       COALESCE(AVG(latency_ms), 0) as avg_latency
                FROM runs
                WHERE start_time >= ? AND start_time < ? AND tenant_id = ?
                """,
                Timestamp.from(previousWindowStart), Timestamp.from(windowStart), tenantId
            );

            jdbc.query(
                """
                SELECT LEAST(23, GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (start_time - ?)) / ?)::int)) as bucket,
                       COUNT(*) as count,
                       COALESCE(SUM(total_tokens), 0) as tokens,
                       COALESCE(SUM(total_cost), 0) as cost,
                       COALESCE(AVG(latency_ms), 0) as latency
                FROM runs
                WHERE start_time >= ? AND start_time < ? AND tenant_id = ?
                GROUP BY bucket
                ORDER BY bucket
                """,
                rs -> {
                    int bucket = rs.getInt("bucket");
                    if (bucket >= 0 && bucket < 24) {
                        runsSpark.set(bucket, ((Number) rs.getObject("count")).intValue());
                        tokensSpark.set(bucket, ((Number) rs.getObject("tokens")).intValue());
                        costSpark.set(bucket, (int) Math.round(rs.getDouble("cost") * 100));
                        latencySpark.set(bucket, (int) Math.round(rs.getDouble("latency")));
                    }
                },
                Timestamp.from(windowStart), bucketSeconds, Timestamp.from(windowStart), Timestamp.from(now), tenantId
            );

            runsByDay = jdbc.query(
                """
                SELECT DATE(start_time) as day,
                       COUNT(*) as count,
                       COALESCE(SUM(total_tokens), 0) as tokens,
                       COALESCE(SUM(total_cost), 0) as cost
                FROM runs
                WHERE start_time >= ? AND start_time < ? AND tenant_id = ?
                GROUP BY DATE(start_time)
                ORDER BY day ASC
                """,
                (rs, rowNum) -> new DayMetrics(
                    rs.getDate("day").toLocalDate().toString(),
                    rs.getLong("count"),
                    rs.getLong("tokens"),
                    rs.getBigDecimal("cost").doubleValue()
                ),
                Timestamp.from(windowStart), Timestamp.from(now), tenantId
            );

            topModels = jdbc.query(
                """
                SELECT model,
                       COUNT(*) as calls,
                       COALESCE(SUM(total_tokens), 0) as tokens,
                       COALESCE(SUM(total_cost), 0) as cost
                FROM runs
                WHERE start_time >= ? AND start_time < ? AND model IS NOT NULL AND tenant_id = ?
                GROUP BY model
                ORDER BY calls DESC
                LIMIT 5
                """,
                (rs, rowNum) -> new ModelMetrics(
                    rs.getString("model"),
                    inferProvider(rs.getString("model")),
                    rs.getLong("calls"),
                    rs.getLong("tokens"),
                    rs.getBigDecimal("cost").doubleValue()
                ),
                Timestamp.from(windowStart), Timestamp.from(now), tenantId
            );

            topAgents = jdbc.query(
                """
                SELECT agent_id,
                       MAX(framework) as framework,
                       COUNT(*) as runs,
                       COALESCE(SUM(total_tokens), 0) as tokens,
                       COALESCE(SUM(total_cost), 0) as cost,
                       COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) as p95,
                       SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) as errors
                FROM runs
                WHERE start_time >= ? AND start_time < ? AND tenant_id = ?
                GROUP BY agent_id
                ORDER BY runs DESC
                LIMIT 5
                """,
                (rs, rowNum) -> new AgentMetrics(
                    rs.getString("agent_id"),
                    rs.getString("framework"),
                    rs.getLong("runs"),
                    rs.getLong("tokens"),
                    rs.getBigDecimal("cost").doubleValue(),
                    rs.getObject("p95") != null ? ((Number) rs.getObject("p95")).longValue() : 0L,
                    rs.getLong("errors")
                ),
                Timestamp.from(windowStart), Timestamp.from(now), tenantId
            );

            statusBreakdown = jdbc.query(
                """
                SELECT status, COUNT(*) as count
                FROM runs
                WHERE start_time >= ? AND start_time < ? AND tenant_id = ?
                GROUP BY status
                """,
                (rs, rowNum) -> new StatusMetrics(
                    rs.getString("status"),
                    rs.getLong("count"),
                    0.0
                ),
                Timestamp.from(windowStart), Timestamp.from(now), tenantId
            );
        } else {
            // Current window overall stats (global fallback)
            currentOverall = jdbc.queryForMap(
                """
                SELECT COUNT(*) as total_runs,
                       COALESCE(SUM(total_tokens), 0) as total_tokens,
                       COALESCE(SUM(total_cost), 0) as total_cost,
                       COALESCE(AVG(latency_ms), 0) as avg_latency,
                       COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) as p95_latency,
                       COALESCE(SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END), 0) as error_count
                FROM runs
                WHERE start_time >= ? AND start_time < ?
                """,
                Timestamp.from(windowStart), Timestamp.from(now)
            );

            // Previous window overall stats (global fallback)
            previousOverall = jdbc.queryForMap(
                """
                SELECT COUNT(*) as total_runs,
                       COALESCE(SUM(total_tokens), 0) as total_tokens,
                       COALESCE(SUM(total_cost), 0) as total_cost,
                       COALESCE(AVG(latency_ms), 0) as avg_latency
                FROM runs
                WHERE start_time >= ? AND start_time < ?
                """,
                Timestamp.from(previousWindowStart), Timestamp.from(windowStart)
            );

            jdbc.query(
                """
                SELECT LEAST(23, GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (start_time - ?)) / ?)::int)) as bucket,
                       COUNT(*) as count,
                       COALESCE(SUM(total_tokens), 0) as tokens,
                       COALESCE(SUM(total_cost), 0) as cost,
                       COALESCE(AVG(latency_ms), 0) as latency
                FROM runs
                WHERE start_time >= ? AND start_time < ?
                GROUP BY bucket
                ORDER BY bucket
                """,
                rs -> {
                    int bucket = rs.getInt("bucket");
                    if (bucket >= 0 && bucket < 24) {
                        runsSpark.set(bucket, ((Number) rs.getObject("count")).intValue());
                        tokensSpark.set(bucket, ((Number) rs.getObject("tokens")).intValue());
                        costSpark.set(bucket, (int) Math.round(rs.getDouble("cost") * 100));
                        latencySpark.set(bucket, (int) Math.round(rs.getDouble("latency")));
                    }
                },
                Timestamp.from(windowStart), bucketSeconds, Timestamp.from(windowStart), Timestamp.from(now)
            );

            runsByDay = jdbc.query(
                """
                SELECT DATE(start_time) as day,
                       COUNT(*) as count,
                       COALESCE(SUM(total_tokens), 0) as tokens,
                       COALESCE(SUM(total_cost), 0) as cost
                FROM runs
                WHERE start_time >= ? AND start_time < ?
                GROUP BY DATE(start_time)
                ORDER BY day ASC
                """,
                (rs, rowNum) -> new DayMetrics(
                    rs.getDate("day").toLocalDate().toString(),
                    rs.getLong("count"),
                    rs.getLong("tokens"),
                    rs.getBigDecimal("cost").doubleValue()
                ),
                Timestamp.from(windowStart), Timestamp.from(now)
            );

            topModels = jdbc.query(
                """
                SELECT model,
                       COUNT(*) as calls,
                       COALESCE(SUM(total_tokens), 0) as tokens,
                       COALESCE(SUM(total_cost), 0) as cost
                FROM runs
                WHERE start_time >= ? AND start_time < ? AND model IS NOT NULL
                GROUP BY model
                ORDER BY calls DESC
                LIMIT 5
                """,
                (rs, rowNum) -> new ModelMetrics(
                    rs.getString("model"),
                    inferProvider(rs.getString("model")),
                    rs.getLong("calls"),
                    rs.getLong("tokens"),
                    rs.getBigDecimal("cost").doubleValue()
                ),
                Timestamp.from(windowStart), Timestamp.from(now)
            );

            topAgents = jdbc.query(
                """
                SELECT agent_id,
                       MAX(framework) as framework,
                       COUNT(*) as runs,
                       COALESCE(SUM(total_tokens), 0) as tokens,
                       COALESCE(SUM(total_cost), 0) as cost,
                       COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) as p95,
                       SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) as errors
                FROM runs
                WHERE start_time >= ? AND start_time < ?
                GROUP BY agent_id
                ORDER BY runs DESC
                LIMIT 5
                """,
                (rs, rowNum) -> new AgentMetrics(
                    rs.getString("agent_id"),
                    rs.getString("framework"),
                    rs.getLong("runs"),
                    rs.getLong("tokens"),
                    rs.getBigDecimal("cost").doubleValue(),
                    rs.getObject("p95") != null ? ((Number) rs.getObject("p95")).longValue() : 0L,
                    rs.getLong("errors")
                ),
                Timestamp.from(windowStart), Timestamp.from(now)
            );

            statusBreakdown = jdbc.query(
                """
                SELECT status, COUNT(*) as count
                FROM runs
                WHERE start_time >= ? AND start_time < ?
                GROUP BY status
                """,
                (rs, rowNum) -> new StatusMetrics(
                    rs.getString("status"),
                    rs.getLong("count"),
                    0.0
                ),
                Timestamp.from(windowStart), Timestamp.from(now)
            );
        }

        long totalRuns = ((Number) currentOverall.get("total_runs")).longValue();
        long totalTokens = ((Number) currentOverall.get("total_tokens")).longValue();
        double totalCost = ((Number) currentOverall.get("total_cost")).doubleValue();
        double avgLatencyMs = ((Number) currentOverall.get("avg_latency")).doubleValue();
        double p95LatencyMs = ((Number) currentOverall.get("p95_latency")).doubleValue();
        long errorCount = ((Number) currentOverall.get("error_count")).longValue();

        long prevRuns = ((Number) previousOverall.get("total_runs")).longValue();
        long prevTokens = ((Number) previousOverall.get("total_tokens")).longValue();
        double prevCost = ((Number) previousOverall.get("total_cost")).doubleValue();
        double prevLatency = ((Number) previousOverall.get("avg_latency")).doubleValue();

        double errorRate = totalRuns > 0 ? (errorCount * 100.0) / totalRuns : 0.0;
        double runsDelta = prevRuns > 0 ? ((totalRuns - prevRuns) * 100.0) / prevRuns : 0.0;
        double tokensDelta = prevTokens > 0 ? ((totalTokens - prevTokens) * 100.0) / prevTokens : 0.0;
        double costDelta = prevCost > 0 ? ((totalCost - prevCost) * 100.0) / prevCost : 0.0;
        double latencyDelta = prevLatency > 0 ? ((avgLatencyMs - prevLatency) * 100.0) / prevLatency : 0.0;

        List<StatusMetrics> statusWithPct = statusBreakdown.stream()
            .map(s -> new StatusMetrics(
                s.status(),
                s.count(),
                totalRuns > 0 ? (s.count() * 100.0) / totalRuns : 0.0
            ))
            .toList();

        return new DashboardMetrics(
            totalRuns,
            totalTokens,
            totalCost,
            avgLatencyMs,
            p95LatencyMs,
            errorRate,
            runsDelta,
            tokensDelta,
            costDelta,
            latencyDelta,
            runsSpark,
            tokensSpark,
            costSpark,
            latencySpark,
            runsByDay,
            topModels,
            topAgents,
            statusWithPct
        );
    }

    public @NonNull List<List<Integer>> getHeatmap(@NonNull String window) {
        Instant now = Instant.now();
        long windowHours = parseWindow(window);
        Instant start = now.minus(windowHours, ChronoUnit.HOURS);

        int[][] grid = new int[7][24];
        String tenantId = TenantContext.getTenantIdOrNull();

        if (tenantId != null) {
            jdbc.query(
                """
                SELECT EXTRACT(DOW FROM start_time)::int as dow,
                       EXTRACT(HOUR FROM start_time)::int as hr,
                       COUNT(*) as cnt
                FROM runs
                WHERE start_time >= ? AND tenant_id = ?
                GROUP BY dow, hr
                """,
                rs -> {
                    int dow = rs.getInt("dow");
                    int hr = rs.getInt("hr");
                    if (dow >= 0 && dow < 7 && hr >= 0 && hr < 24) {
                        grid[dow][hr] = rs.getInt("cnt");
                    }
                },
                Timestamp.from(start), tenantId
            );
        } else {
            jdbc.query(
                """
                SELECT EXTRACT(DOW FROM start_time)::int as dow,
                       EXTRACT(HOUR FROM start_time)::int as hr,
                       COUNT(*) as cnt
                FROM runs
                WHERE start_time >= ?
                GROUP BY dow, hr
                """,
                rs -> {
                    int dow = rs.getInt("dow");
                    int hr = rs.getInt("hr");
                    if (dow >= 0 && dow < 7 && hr >= 0 && hr < 24) {
                        grid[dow][hr] = rs.getInt("cnt");
                    }
                },
                Timestamp.from(start)
            );
        }

        List<List<Integer>> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            List<Integer> row = new ArrayList<>(24);
            for (int j = 0; j < 24; j++) {
                row.add(grid[i][j]);
            }
            result.add(row);
        }
        return result;
    }

    private static long parseWindow(@NonNull String window) {
        return switch (window) {
            case "24h" -> 24;
            case "7d" -> 24 * 7;
            case "30d" -> 24 * 30;
            default -> 24;
        };
    }

    static @NonNull String inferProvider(@NonNull String model) {
        String m = model.toLowerCase();
        // Strip provider prefix (e.g. "anthropic/claude-opus-4-7" → "claude-opus-4-7")
        int slash = m.indexOf('/');
        if (slash >= 0) {
            String prefix = m.substring(0, slash);
            m = m.substring(slash + 1);
            // trust explicit provider prefix if recognisable
            return switch (prefix) {
                case "anthropic"    -> "anthropic";
                case "openai"       -> "openai";
                case "google", "google-cloud", "vertex_ai" -> "google";
                case "meta", "groq", "together", "fireworks" -> inferProviderFromName(m);
                case "mistral"      -> "mistral";
                case "deepseek"     -> "deepseek";
                case "cohere"       -> "cohere";
                case "aws", "bedrock" -> inferProviderFromName(m);
                default             -> inferProviderFromName(m);
            };
        }
        return inferProviderFromName(m);
    }

    private static @NonNull String inferProviderFromName(@NonNull String m) {
        if (m.startsWith("claude-"))        return "anthropic";
        if (m.startsWith("gpt-"))           return "openai";
        if (m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4")) return "openai";
        if (m.startsWith("text-embedding-") || m.startsWith("dall-e-")) return "openai";
        if (m.startsWith("gemini-"))        return "google";
        if (m.startsWith("palm-") || m.startsWith("bison-")) return "google";
        if (m.startsWith("deepseek-"))      return "deepseek";
        if (m.startsWith("llama-") || m.startsWith("meta-")) return "meta";
        if (m.startsWith("mistral-") || m.startsWith("mixtral-") || m.startsWith("codestral")) return "mistral";
        if (m.startsWith("command-"))       return "cohere";
        if (m.startsWith("titan-") || m.startsWith("nova-")) return "aws";
        if (m.startsWith("qwen-") || m.startsWith("qwq-")) return "alibaba";
        if (m.startsWith("yi-"))            return "01.ai";
        if (m.startsWith("phi-"))           return "microsoft";
        return "unknown";
    }

    public record DashboardMetrics(
        long totalRuns,
        long totalTokens,
        double totalCost,
        double avgLatencyMs,
        double p95LatencyMs,
        double errorRate,
        double runsDelta,
        double tokensDelta,
        double costDelta,
        double latencyDelta,
        List<Integer> runsSpark,
        List<Integer> tokensSpark,
        List<Integer> costSpark,
        List<Integer> latencySpark,
        List<DayMetrics> runsByDay,
        List<ModelMetrics> topModels,
        List<AgentMetrics> topAgents,
        List<StatusMetrics> statusBreakdown
    ) {}

    public record DayMetrics(String date, long count, long tokens, double cost) {}
    public record ModelMetrics(String model, String provider, long calls, long tokens, double cost) {}
    public record AgentMetrics(String agentId, String framework, long runs, long tokens, double cost, long p95, long errors) {}
    public record StatusMetrics(String status, long count, double pct) {}
}
