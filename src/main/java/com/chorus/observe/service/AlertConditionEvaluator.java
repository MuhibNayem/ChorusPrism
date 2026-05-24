package com.chorus.observe.service;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evaluates alert rule conditions against the trace database.
 * <p>
 * Supports two condition expression formats:
 * <ul>
 *   <li><b>SQL:</b> {@code sql:SELECT COUNT(*) FROM runs WHERE status = 'ERROR' AND start_time > NOW() - INTERVAL '5 minutes'}
 *       — executes the query with defense-in-depth protections and compares the first numeric column against the threshold.</li>
 *   <li><b>Metric:</b> {@code metric:ingestion.spans.total} — reserved for future Micrometer integration.</li>
 * </ul>
 * <p>
 * SQL conditions are executed with a 10-second query timeout, 1-row limit,
 * read-only role enforcement, and the same security layers as {@link SqlQueryService}.
 */
public class AlertConditionEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(AlertConditionEvaluator.class);
    private static final int QUERY_TIMEOUT_SECONDS = 10;
    private static final int MAX_ROWS = 1;

    private static final Pattern VALID_ROLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER",
        "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL",
        "MERGE", "UPSERT", "REPLACE", "COPY", "LOAD"
    );

    private final JdbcTemplate jdbc;
    private final String readOnlyRole;

    public AlertConditionEvaluator(@NonNull DataSource dataSource) {
        this(dataSource, null);
    }

    public AlertConditionEvaluator(@NonNull DataSource dataSource, @Nullable String readOnlyRole) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
        this.jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.readOnlyRole = readOnlyRole;
    }

    /**
     * Evaluate a condition expression and return the numeric value.
     *
     * @param conditionExpr the condition expression (e.g., "sql:SELECT ...")
     * @return the evaluated numeric value, or {@code null} if evaluation failed
     */
    public @Nullable Double evaluate(@NonNull String conditionExpr) {
        String expr = conditionExpr.trim();
        if (expr.startsWith("sql:")) {
            return evaluateSql(expr.substring(4).trim());
        }
        if (expr.startsWith("metric:")) {
            return evaluateMetric(expr.substring(7).trim());
        }
        LOG.warn("Unknown condition expression format: {}", conditionExpr);
        return null;
    }

    private @Nullable Double evaluateSql(@NonNull String sql) {
        String normalized = normalize(sql);
        if (!normalized.startsWith("SELECT ")) {
            LOG.warn("SQL condition must start with SELECT: {}", sql);
            return null;
        }
        if (normalized.contains(";")) {
            LOG.warn("SQL condition contains semicolon (multiple statements not allowed): {}", sql);
            return null;
        }
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (containsWord(normalized, keyword)) {
                LOG.warn("SQL condition contains forbidden keyword '{}': {}", keyword, sql);
                return null;
            }
        }

        return jdbc.execute((ConnectionCallback<Double>) con -> {
            enforceReadOnlyRole(con);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setMaxRows(MAX_ROWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return 0.0;
                    }
                    Object value = rs.getObject(1);
                    return extractNumber(value);
                }
            }
        });
    }

    private @Nullable Double extractNumber(@Nullable Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private @NonNull String normalize(@NonNull String sql) {
        String s = sql.trim();
        s = s.replaceAll("'[^']*'", "''");
        s = s.replaceAll("\"[^\"]*\"", "\"\"");
        s = s.replaceAll("/\\*.*?\\*/", " ");
        s = s.replaceAll("--[^\\n]*", " ");
        return s.toUpperCase();
    }

    private boolean containsWord(@NonNull String text, @NonNull String word) {
        return text.matches(".*\\b" + word + "\\b.*");
    }

    private void enforceReadOnlyRole(@NonNull Connection con) throws SQLException {
        if (readOnlyRole == null || readOnlyRole.isBlank()) {
            return;
        }
        if (!VALID_ROLE_NAME.matcher(readOnlyRole).matches()) {
            LOG.error("Invalid read-only role name '{}'. Failing closed.", readOnlyRole);
            throw new IllegalStateException(
                "Invalid read-only role name '" + readOnlyRole + "'. Query aborted for security.");
        }
        try (Statement stmt = con.createStatement()) {
            stmt.execute("SET ROLE " + readOnlyRole);
        } catch (SQLException e) {
            LOG.error("Failed to set read-only role '{}'. Failing closed.", readOnlyRole, e);
            throw new IllegalStateException(
                "Cannot enforce read-only role '" + readOnlyRole + "'. Query aborted for security.", e);
        }
    }

    /**
     * Evaluate a metric condition by querying the {@link MetricsService} or
     * falling back to a database-backed metric lookup.
     * <p>
     * Supported metric names:
     * <ul>
     *   <li>{@code ingestion.spans.total} — total spans ingested (counter)</li>
     *   <li>{@code ingestion.errors.total} — spans with ERROR status in last 5 min</li>
     *   <li>{@code eval.runs.total} — total evaluation runs (counter)</li>
     *   <li>{@code redteam.runs.total} — total red team runs (counter)</li>
     * </ul>
     *
     * @param metricName the metric identifier (without {@code metric:} prefix)
     * @return the current numeric value, or {@code null} if unavailable
     */
    private @Nullable Double evaluateMetric(@NonNull String metricName) {
        // Database-backed metric queries for time-windowed alerting
        String sql = switch (metricName.toLowerCase()) {
            case "ingestion.spans.total" ->
                "SELECT COUNT(*) FROM spans WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5' MINUTE";
            case "ingestion.errors.total" ->
                "SELECT COUNT(*) FROM spans WHERE status = 'ERROR' AND created_at > CURRENT_TIMESTAMP - INTERVAL '5' MINUTE";
            case "eval.runs.total" ->
                "SELECT COUNT(*) FROM eval_runs WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5' MINUTE";
            case "redteam.runs.total" ->
                "SELECT COUNT(*) FROM red_team_runs WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5' MINUTE";
            case "llm.calls.total" ->
                "SELECT COUNT(*) FROM llm_calls WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5' MINUTE";
            default -> {
                LOG.warn("Unknown metric '{}'; no SQL mapping defined", metricName);
                yield null;
            }
        };
        if (sql == null) return null;

        return jdbc.execute((ConnectionCallback<Double>) con -> {
            enforceReadOnlyRole(con);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setMaxRows(MAX_ROWS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 0.0;
                    return extractNumber(rs.getObject(1));
                }
            }
        });
    }
}
