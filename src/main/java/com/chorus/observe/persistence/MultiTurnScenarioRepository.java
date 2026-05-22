package com.chorus.observe.persistence;

import com.chorus.observe.model.MultiTurnScenario;
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

public class MultiTurnScenarioRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<MultiTurnScenario> rowMapper;

    public MultiTurnScenarioRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new ScenarioRowMapper(mapper);
    }

    public void save(@NonNull MultiTurnScenario scenario) {
        String sql = """
            INSERT INTO multi_turn_scenarios (scenario_id, name, description, turns, metadata, created_at)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (scenario_id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                turns = EXCLUDED.turns,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            scenario.scenarioId(), scenario.name(), scenario.description(),
            toJson(scenario.turns()), toJson(scenario.metadata()),
            Timestamp.from(scenario.createdAt()));
    }

    public @NonNull Optional<MultiTurnScenario> findById(@NonNull String scenarioId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM multi_turn_scenarios WHERE scenario_id = ?", rowMapper, scenarioId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<MultiTurnScenario> findAll() {
        return jdbc.query("SELECT * FROM multi_turn_scenarios ORDER BY created_at DESC", rowMapper);
    }

    public void deleteById(@NonNull String scenarioId) {
        jdbc.update("DELETE FROM multi_turn_scenarios WHERE scenario_id = ?", scenarioId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class ScenarioRowMapper implements RowMapper<MultiTurnScenario> {
        private final ObjectMapper mapper;

        ScenarioRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public MultiTurnScenario mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new MultiTurnScenario(
                    rs.getString("scenario_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    mapper.readValue(rs.getString("turns"), new TypeReference<List<MultiTurnScenario.Turn>>() {}),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
