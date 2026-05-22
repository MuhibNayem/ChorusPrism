package com.chorus.observe.persistence;

import com.chorus.observe.model.BudgetEnforcement;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository for budget enforcements.
 */
public class BudgetEnforcementRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<BudgetEnforcement> rowMapper = new BudgetEnforcementRowMapper();

    public BudgetEnforcementRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull BudgetEnforcement enforcement) {
        String sql = """
            INSERT INTO budget_enforcements (enforcement_id, agent_id, budget_type, limit_value, current_value, currency, status, triggered_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (enforcement_id) DO UPDATE SET
                agent_id = EXCLUDED.agent_id,
                budget_type = EXCLUDED.budget_type,
                limit_value = EXCLUDED.limit_value,
                current_value = EXCLUDED.current_value,
                currency = EXCLUDED.currency,
                status = EXCLUDED.status,
                triggered_at = EXCLUDED.triggered_at,
                updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            enforcement.enforcementId(), enforcement.agentId(), enforcement.budgetType(),
            enforcement.limitValue(), enforcement.currentValue(), enforcement.currency(),
            enforcement.status().name(),
            enforcement.triggeredAt() != null ? Timestamp.from(enforcement.triggeredAt()) : null,
            Timestamp.from(enforcement.createdAt()), Timestamp.from(enforcement.updatedAt())
        );
    }

    public @NonNull Optional<BudgetEnforcement> findById(@NonNull String enforcementId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM budget_enforcements WHERE enforcement_id = ?", rowMapper, enforcementId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<BudgetEnforcement> findByAgentId(@NonNull String agentId) {
        return jdbc.query("SELECT * FROM budget_enforcements WHERE agent_id = ? ORDER BY created_at DESC", rowMapper, agentId);
    }

    public @NonNull List<BudgetEnforcement> findByAgentId(@NonNull String agentId, int limit, int offset) {
        return jdbc.query("SELECT * FROM budget_enforcements WHERE agent_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, agentId, limit, offset);
    }

    public long countByAgentId(@NonNull String agentId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM budget_enforcements WHERE agent_id = ?", Long.class, agentId);
        return count != null ? count : 0L;
    }

    public @NonNull List<BudgetEnforcement> findAll() {
        return jdbc.query("SELECT * FROM budget_enforcements ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<BudgetEnforcement> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM budget_enforcements ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM budget_enforcements", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<BudgetEnforcement> findActive() {
        return jdbc.query("SELECT * FROM budget_enforcements WHERE status = 'ACTIVE' ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<BudgetEnforcement> findActive(int limit, int offset) {
        return jdbc.query("SELECT * FROM budget_enforcements WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long countActive() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM budget_enforcements WHERE status = 'ACTIVE'", Long.class);
        return count != null ? count : 0L;
    }

    /**
     * Atomically add delta to current_value and update status if thresholds crossed.
     * Uses a single UPDATE to prevent lost updates under concurrent load.
     *
     * @return number of rows updated (1 = success, 0 = budget not found or concurrent modification)
     */
    public int addSpendingAtomic(@NonNull String enforcementId, @NonNull BigDecimal delta) {
        String sql = """
            UPDATE budget_enforcements
            SET current_value = current_value + ?,
                status = CASE
                    WHEN current_value + ? >= limit_value THEN 'EXCEEDED'
                    WHEN current_value + ? >= limit_value * 0.8 AND status = 'ACTIVE' THEN 'WARNING'
                    ELSE status
                END,
                triggered_at = CASE
                    WHEN current_value + ? >= limit_value AND triggered_at IS NULL THEN ?
                    ELSE triggered_at
                END,
                updated_at = ?
            WHERE enforcement_id = ?
            """;
        Instant now = Instant.now();
        return jdbc.update(sql,
            delta, delta, delta, delta,
            Timestamp.from(now), Timestamp.from(now),
            enforcementId
        );
    }

    private static final class BudgetEnforcementRowMapper implements RowMapper<BudgetEnforcement> {
        @Override
        public BudgetEnforcement mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new BudgetEnforcement(
                rs.getString("enforcement_id"),
                rs.getString("agent_id"),
                rs.getString("budget_type"),
                rs.getBigDecimal("limit_value"),
                rs.getBigDecimal("current_value"),
                rs.getString("currency"),
                BudgetEnforcement.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("triggered_at") != null ? rs.getTimestamp("triggered_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
