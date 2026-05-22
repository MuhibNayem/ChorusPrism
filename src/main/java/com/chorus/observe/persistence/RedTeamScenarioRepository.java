package com.chorus.observe.persistence;

import com.chorus.observe.model.RedTeamScenario;
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

/**
 * JDBC repository for red team scenarios.
 */
public class RedTeamScenarioRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<RedTeamScenario> rowMapper;

    public RedTeamScenarioRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RedTeamScenarioRowMapper(mapper);
    }

    public void save(@NonNull RedTeamScenario scenario) {
        String sql = """
            INSERT INTO red_team_scenarios (scenario_id, name, category, attack_prompt, expected_behavior, severity, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (scenario_id) DO UPDATE SET
                name = EXCLUDED.name,
                category = EXCLUDED.category,
                attack_prompt = EXCLUDED.attack_prompt,
                expected_behavior = EXCLUDED.expected_behavior,
                severity = EXCLUDED.severity,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            scenario.scenarioId(), scenario.name(), scenario.category(),
            scenario.attackPrompt(), scenario.expectedBehavior(),
            scenario.severity().name(), toJson(scenario.metadata()),
            Timestamp.from(scenario.createdAt())
        );
    }

    public @NonNull Optional<RedTeamScenario> findById(@NonNull String scenarioId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM red_team_scenarios WHERE scenario_id = ?", rowMapper, scenarioId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<RedTeamScenario> findAll() {
        return jdbc.query("SELECT * FROM red_team_scenarios ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<RedTeamScenario> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM red_team_scenarios ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_scenarios", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<RedTeamScenario> findByCategory(@NonNull String category) {
        return jdbc.query("SELECT * FROM red_team_scenarios WHERE category = ? ORDER BY created_at DESC", rowMapper, category);
    }

    public @NonNull List<RedTeamScenario> findByCategory(@NonNull String category, int limit, int offset) {
        return jdbc.query("SELECT * FROM red_team_scenarios WHERE category = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, category, limit, offset);
    }

    public long countByCategory(@NonNull String category) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_scenarios WHERE category = ?", Long.class, category);
        return count != null ? count : 0L;
    }

    public @NonNull List<RedTeamScenario> findBySeverity(RedTeamScenario.Severity severity) {
        return jdbc.query("SELECT * FROM red_team_scenarios WHERE severity = ? ORDER BY created_at DESC", rowMapper, severity.name());
    }

    public @NonNull List<RedTeamScenario> findBySeverity(RedTeamScenario.Severity severity, int limit, int offset) {
        return jdbc.query("SELECT * FROM red_team_scenarios WHERE severity = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, severity.name(), limit, offset);
    }

    public long countBySeverity(RedTeamScenario.Severity severity) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM red_team_scenarios WHERE severity = ?", Long.class, severity.name());
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String scenarioId) {
        jdbc.update("DELETE FROM red_team_scenarios WHERE scenario_id = ?", scenarioId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class RedTeamScenarioRowMapper implements RowMapper<RedTeamScenario> {
        private final ObjectMapper mapper;

        RedTeamScenarioRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public RedTeamScenario mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new RedTeamScenario(
                    rs.getString("scenario_id"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getString("attack_prompt"),
                    rs.getString("expected_behavior"),
                    RedTeamScenario.Severity.valueOf(rs.getString("severity")),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
