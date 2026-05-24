package com.chorus.observe.export;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds parameterized SQL for export queries, applying tenant_id and queryFilter.
 */
public class ExportQueryBuilder {

    public record QueryAndArgs(@NonNull String sql, @NonNull List<Object> args) {}

    public @NonNull QueryAndArgs buildSpanQuery(@NonNull String tenantId, @NonNull Map<String, Object> queryFilter, boolean useClickHouse) {
        if (useClickHouse) {
            throw new UnsupportedOperationException("ClickHouse span export requires two-phase query; use buildRunIdsQuery first");
        }

        StringBuilder sql = new StringBuilder("""
            SELECT s.span_id, s.run_id, s.parent_span_id, s.span_name, s.kind,
                   s.start_time, s.end_time, s.attributes, s.events,
                   s.status, s.span_type, s.first_token_at, r.tenant_id
            FROM spans s
            JOIN runs r ON s.run_id = r.run_id
            WHERE r.tenant_id = ?
            """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        applyFilter(sql, args, queryFilter);
        sql.append(" ORDER BY s.start_time ASC");

        return new QueryAndArgs(sql.toString(), args);
    }

    public @NonNull QueryAndArgs buildMetricQuery(@NonNull String tenantId, @NonNull Map<String, Object> queryFilter) {
        StringBuilder sql = new StringBuilder("""
            SELECT snapshot_id, tenant_id, metric_name, value, tags::text, timestamp
            FROM metric_snapshots
            WHERE tenant_id = ?
            """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        applyFilter(sql, args, queryFilter);
        sql.append(" ORDER BY timestamp ASC");

        return new QueryAndArgs(sql.toString(), args);
    }

    public @NonNull QueryAndArgs buildRunIdsQuery(@NonNull String tenantId, @NonNull Map<String, Object> queryFilter) {
        StringBuilder sql = new StringBuilder("SELECT run_id FROM runs WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        applyFilter(sql, args, queryFilter);
        return new QueryAndArgs(sql.toString(), args);
    }

    private void applyFilter(@NonNull StringBuilder sql, @NonNull List<Object> args, @NonNull Map<String, Object> queryFilter) {
        Instant from = parseInstant(queryFilter.get("from"));
        Instant to = parseInstant(queryFilter.get("to"));
        String agentId = parseString(queryFilter.get("agentId"));
        String framework = parseString(queryFilter.get("framework"));
        String status = parseString(queryFilter.get("status"));

        if (from != null) {
            sql.append(" AND start_time >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND start_time <= ?");
            args.add(Timestamp.from(to));
        }
        if (agentId != null) {
            sql.append(" AND agent_id = ?");
            args.add(agentId);
        }
        if (framework != null) {
            sql.append(" AND framework = ?");
            args.add(framework);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
    }

    private @Nullable Instant parseInstant(@Nullable Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof String s) return Instant.parse(s);
        if (value instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        return null;
    }

    private @Nullable String parseString(@Nullable Object value) {
        return value != null ? value.toString() : null;
    }
}
