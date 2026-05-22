package com.chorus.observe.service;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for executing read-only SQL queries against the trace database.
 * <p>
 * Defense-in-depth layers:
 * <ol>
 *   <li>Query must start with SELECT (rejected: INSERT, UPDATE, DELETE, DDL)</li>
 *   <li>Forbidden keyword blocklist (defense against concatenation / comment injection)</li>
 *   <li>Table whitelist: only known schema tables allowed in FROM/JOIN</li>
 *   <li>Single-statement enforcement (no semicolons)</li>
 *   <li>Database-level read-only role ({@code SET ROLE}) — <b>fail-closed</b></li>
 *   <li>JDBC {@code setMaxRows} hard limit (works across all dialects)</li>
 *   <li>JDBC {@code setQueryTimeout} execution timeout</li>
 * </ol>
 */
public class SqlQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(SqlQueryService.class);

    private static final int MAX_ROWS = 10_000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "runs", "spans", "llm_calls", "tool_calls", "feedback",
        "datasets", "dataset_items", "eval_runs", "eval_results",
        "checkpoints", "replay_runs", "breakpoints", "red_team_scenarios",
        "red_team_runs", "red_team_results", "guardrail_telemetry",
        "metric_snapshots", "rag_queries", "provenance_entries",
        "prompt_versions", "prompt_tags", "prompt_ab_tests",
        "trace_clusters", "budget_enforcements", "alert_rules",
        "alert_events", "ch_spans", "ch_llm_calls", "ch_tool_calls",
        "sql_query_logs"
    );

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER",
        "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL",
        "MERGE", "UPSERT", "REPLACE", "COPY", "LOAD"
    );

    private static final Pattern FROM_TABLES_PATTERN = Pattern.compile(
        "(?i)\\bFROM\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?(?:\\s*,\\s*[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)*)"
    );
    private static final Pattern JOIN_TABLE_PATTERN = Pattern.compile(
        "(?i)\\bJOIN\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)"
    );

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final String readOnlyRole;

    public SqlQueryService(@NonNull DataSource dataSource) {
        this(dataSource, null);
    }

    public SqlQueryService(@NonNull DataSource dataSource, @Nullable String readOnlyRole) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.readOnlyRole = readOnlyRole;
    }

    /**
     * Execute a read-only SELECT query.
     *
     * @param sql the SQL query (must start with SELECT)
     * @return list of row maps
     * @throws IllegalArgumentException if the query violates security constraints
     * @throws DataAccessException      if the query fails at the database level
     * @throws IllegalStateException    if the read-only role cannot be set and fail-closed is enforced
     */
    public @NonNull List<Map<String, Object>> executeQuery(@NonNull String sql) {
        String normalized = normalize(sql);

        if (!normalized.startsWith("SELECT ")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed. Query: " + sql);
        }
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("Multiple statements are not allowed.");
        }

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (containsWord(normalized, keyword)) {
                throw new IllegalArgumentException("Query contains forbidden keyword: " + keyword);
            }
        }

        validateTables(normalized);

        return jdbc.execute((ConnectionCallback<List<Map<String, Object>>>) con -> {
            enforceReadOnlyRole(con);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setMaxRows(MAX_ROWS);
                ResultSet rs = ps.executeQuery();
                ColumnMapRowMapper mapper = new ColumnMapRowMapper();
                List<Map<String, Object>> results = new ArrayList<>();
                int rowNum = 0;
                while (rs.next() && rowNum < MAX_ROWS) {
                    results.add(mapper.mapRow(rs, rowNum++));
                }
                return results;
            }
        });
    }

    /**
     * Normalize SQL for security scanning. Strips string literals and comments
     * to prevent injection via comments or literal contents.
     */
    private @NonNull String normalize(@NonNull String sql) {
        String s = sql.trim();
        // Strip single-quoted strings (replace with '' to preserve length but remove content)
        s = s.replaceAll("'[^']*'", "''");
        // Strip double-quoted identifiers (PostgreSQL)
        s = s.replaceAll("\"[^\"]*\"", "\"\"");
        // Strip /* */ comments
        s = s.replaceAll("/\\*.*?\\*/", " ");
        // Strip -- line comments
        s = s.replaceAll("--[^\\n]*", " ");
        return s.toUpperCase();
    }

    private boolean containsWord(@NonNull String text, @NonNull String word) {
        // Match the keyword as a whole word to avoid false positives like "selection"
        return text.matches(".*\\b" + word + "\\b.*");
    }

    private void validateTables(@NonNull String normalizedSql) {
        Matcher fromMatcher = FROM_TABLES_PATTERN.matcher(normalizedSql);
        while (fromMatcher.find()) {
            String tables = fromMatcher.group(1);
            for (String t : tables.split(",")) {
                String table = stripSchema(t.trim());
                if (!ALLOWED_TABLES.contains(table.toLowerCase())) {
                    throw new IllegalArgumentException("Table not allowed: " + table);
                }
            }
        }

        Matcher joinMatcher = JOIN_TABLE_PATTERN.matcher(normalizedSql);
        while (joinMatcher.find()) {
            String table = stripSchema(joinMatcher.group(1).trim());
            if (!ALLOWED_TABLES.contains(table.toLowerCase())) {
                throw new IllegalArgumentException("Table not allowed: " + table);
            }
        }
    }

    private @NonNull String stripSchema(@NonNull String tableRef) {
        int dot = tableRef.lastIndexOf('.');
        return dot >= 0 ? tableRef.substring(dot + 1) : tableRef;
    }

    private static final Pattern VALID_ROLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private void enforceReadOnlyRole(@NonNull Connection con) throws SQLException {
        if (readOnlyRole == null || readOnlyRole.isBlank()) {
            LOG.debug("No read-only role configured; skipping SET ROLE");
            return;
        }
        if (!VALID_ROLE_NAME.matcher(readOnlyRole).matches()) {
            LOG.error("Invalid read-only role name '{}'. Failing closed.", readOnlyRole);
            throw new IllegalStateException(
                "Invalid read-only role name '" + readOnlyRole + "'. Query aborted for security.");
        }
        try (Statement stmt = con.createStatement()) {
            stmt.execute("SET ROLE " + readOnlyRole);
            LOG.debug("Read-only role set: {}", readOnlyRole);
        } catch (SQLException e) {
            LOG.error("Failed to set read-only role '{}'. Failing closed.", readOnlyRole, e);
            throw new IllegalStateException(
                "Cannot enforce read-only role '" + readOnlyRole + "'. Query aborted for security.", e);
        }
    }
}
