package com.chorus.observe.persistence;

import com.chorus.observe.model.Breakpoint;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository for breakpoints.
 */
public class BreakpointRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Breakpoint> rowMapper = new BreakpointRowMapper();

    public BreakpointRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull Breakpoint breakpoint) {
        String tenantId = TenantContext.getTenantIdOrNull();
        String sql = """
            INSERT INTO breakpoints (breakpoint_id, tenant_id, run_id, before_node, before_tool, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (breakpoint_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                run_id = EXCLUDED.run_id,
                before_node = EXCLUDED.before_node,
                before_tool = EXCLUDED.before_tool,
                status = EXCLUDED.status
            """;
        jdbc.update(sql,
            breakpoint.breakpointId(), tenantId != null ? tenantId : "default", breakpoint.runId(),
            breakpoint.beforeNode(), breakpoint.beforeTool(),
            breakpoint.status().name(), Timestamp.from(breakpoint.createdAt())
        );
    }

    public @NonNull Optional<Breakpoint> findById(@NonNull String breakpointId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        try {
            if (tenantId != null) {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM breakpoints WHERE breakpoint_id = ? AND tenant_id = ?", rowMapper, breakpointId, tenantId));
            }
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM breakpoints WHERE breakpoint_id = ?", rowMapper, breakpointId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Breakpoint> findByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM breakpoints WHERE run_id = ? AND tenant_id = ? ORDER BY created_at DESC", rowMapper, runId, tenantId);
        }
        return jdbc.query("SELECT * FROM breakpoints WHERE run_id = ? ORDER BY created_at DESC", rowMapper, runId);
    }

    public @NonNull List<Breakpoint> findByRunId(@NonNull String runId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM breakpoints WHERE run_id = ? AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, runId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM breakpoints WHERE run_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, runId, limit, offset);
    }

    public long countByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM breakpoints WHERE run_id = ? AND tenant_id = ?", Long.class, runId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM breakpoints WHERE run_id = ?", Long.class, runId);
        return count != null ? count : 0L;
    }

    public @NonNull List<Breakpoint> findActiveByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM breakpoints WHERE run_id = ? AND status = 'ACTIVE' AND tenant_id = ? ORDER BY created_at DESC", rowMapper, runId, tenantId);
        }
        return jdbc.query("SELECT * FROM breakpoints WHERE run_id = ? AND status = 'ACTIVE' ORDER BY created_at DESC", rowMapper, runId);
    }

    public @NonNull List<Breakpoint> findActiveByRunId(@NonNull String runId, int limit, int offset) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            return jdbc.query("SELECT * FROM breakpoints WHERE run_id = ? AND status = 'ACTIVE' AND tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, runId, tenantId, limit, offset);
        }
        return jdbc.query("SELECT * FROM breakpoints WHERE run_id = ? AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, runId, limit, offset);
    }

    public long countActiveByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM breakpoints WHERE run_id = ? AND status = 'ACTIVE' AND tenant_id = ?", Long.class, runId, tenantId);
            return count != null ? count : 0L;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM breakpoints WHERE run_id = ? AND status = 'ACTIVE'", Long.class, runId);
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String breakpointId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM breakpoints WHERE breakpoint_id = ? AND tenant_id = ?", breakpointId, tenantId);
        } else {
            jdbc.update("DELETE FROM breakpoints WHERE breakpoint_id = ?", breakpointId);
        }
    }

    public void deleteByRunId(@NonNull String runId) {
        String tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            jdbc.update("DELETE FROM breakpoints WHERE run_id = ? AND tenant_id = ?", runId, tenantId);
        } else {
            jdbc.update("DELETE FROM breakpoints WHERE run_id = ?", runId);
        }
    }

    private static final class BreakpointRowMapper implements RowMapper<Breakpoint> {
        @Override
        public Breakpoint mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Breakpoint(
                rs.getString("breakpoint_id"),
                rs.getString("run_id"),
                rs.getString("before_node"),
                rs.getString("before_tool"),
                Breakpoint.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
