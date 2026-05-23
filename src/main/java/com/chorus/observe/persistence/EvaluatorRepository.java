package com.chorus.observe.persistence;

import com.chorus.observe.model.Evaluator;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository for evaluators.
 */
public class EvaluatorRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Evaluator> rowMapper;

    public EvaluatorRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new EvaluatorRowMapper(mapper);
    }

    public void save(@NonNull Evaluator evaluator) {
        String sql = """
            INSERT INTO evaluators (evaluator_id, name, kind, description, config)
            VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (evaluator_id) DO UPDATE SET
                name = EXCLUDED.name,
                kind = EXCLUDED.kind,
                description = EXCLUDED.description,
                config = EXCLUDED.config
            """;
        jdbc.update(sql,
            evaluator.evaluatorId(), evaluator.name(), evaluator.kind(),
            evaluator.description(), toJson(evaluator.config())
        );
    }

    public @NonNull Optional<Evaluator> findById(@NonNull String evaluatorId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM evaluators WHERE evaluator_id = ?", rowMapper, evaluatorId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Evaluator> findAll() {
        return jdbc.query("SELECT * FROM evaluators ORDER BY created_at DESC", rowMapper);
    }

    public void deleteById(@NonNull String evaluatorId) {
        jdbc.update("DELETE FROM evaluators WHERE evaluator_id = ?", evaluatorId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class EvaluatorRowMapper implements RowMapper<Evaluator> {
        private final ObjectMapper mapper;

        EvaluatorRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Evaluator mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Evaluator(
                    rs.getString("evaluator_id"),
                    rs.getString("name"),
                    rs.getString("kind"),
                    rs.getString("description"),
                    mapper.readValue(rs.getString("config"), new TypeReference<Map<String, Object>>() {})
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
