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
            LOG.warn("Metric conditions not yet implemented: {}", conditionExpr);
            return null;
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
        try (Statement stmt = con.createStatement()) {
            stmt.execute("SET ROLE " + readOnlyRole);
        } catch (SQLException e) {
            LOG.error("Failed to set read-only role '{}'. Failing closed.", readOnlyRole, e);
            throw new IllegalStateException(
                "Cannot enforce read-only role '" + readOnlyRole + "'. Query aborted for security.", e);
        }
    }
}
