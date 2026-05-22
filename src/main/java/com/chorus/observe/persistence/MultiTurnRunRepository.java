package com.chorus.observe.persistence;

import com.chorus.observe.model.MultiTurnRun;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MultiTurnRunRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<MultiTurnRun> rowMapper;

    public MultiTurnRunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RunRowMapper(mapper);
    }

    public void save(@NonNull MultiTurnRun run) {
        String sql = """
            INSERT INTO multi_turn_runs (run_id, scenario_id, agent_config, status, total_turns, passed_turns, failed_turns, final_score, summary_metrics, started_at, finished_at, created_at)
            VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (run_id) DO UPDATE SET
                scenario_id = EXCLUDED.scenario_id,
                agent_config = EXCLUDED.agent_config,
                status = EXCLUDED.status,
                total_turns = EXCLUDED.total_turns,
                passed_turns = EXCLUDED.passed_turns,
                failed_turns = EXCLUDED.failed_turns,
                final_score = EXCLUDED.final_score,
                summary_metrics = EXCLUDED.summary_metrics,
                started_at = EXCLUDED.started_at,
                finished_at = EXCLUDED.finished_at
            """;
        jdbc.update(sql,
            run.runId(), run.scenarioId(), toJson(run.agentConfig()), run.status().name(),
            run.totalTurns(), run.passedTurns(), run.failedTurns(), run.finalScore(),
            toJson(run.summaryMetrics()),
            run.startedAt() != null ? Timestamp.from(run.startedAt()) : null,
            run.finishedAt() != null ? Timestamp.from(run.finishedAt()) : null,
            Timestamp.from(run.createdAt()));
    }

    public @NonNull Optional<MultiTurnRun> findById(@NonNull String runId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM multi_turn_runs WHERE run_id = ?", rowMapper, runId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<MultiTurnRun> findByScenarioId(@NonNull String scenarioId) {
        return jdbc.query("SELECT * FROM multi_turn_runs WHERE scenario_id = ? ORDER BY created_at DESC", rowMapper, scenarioId);
    }

    public @NonNull List<MultiTurnRun> findAll() {
        return jdbc.query("SELECT * FROM multi_turn_runs ORDER BY created_at DESC", rowMapper);
    }

    public void deleteById(@NonNull String runId) {
        jdbc.update("DELETE FROM multi_turn_runs WHERE run_id = ?", runId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class RunRowMapper implements RowMapper<MultiTurnRun> {
        private final ObjectMapper mapper;

        RunRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public MultiTurnRun mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new MultiTurnRun(
                    rs.getString("run_id"),
                    rs.getString("scenario_id"),
                    mapper.readValue(rs.getString("agent_config"), new TypeReference<Map<String, Object>>() {}),
                    MultiTurnRun.Status.valueOf(rs.getString("status")),
                    rs.getInt("total_turns"),
                    rs.getInt("passed_turns"),
                    rs.getInt("failed_turns"),
                    rs.getObject("final_score") != null ? rs.getDouble("final_score") : null,
                    mapper.readValue(rs.getString("summary_metrics"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                    rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null,
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
