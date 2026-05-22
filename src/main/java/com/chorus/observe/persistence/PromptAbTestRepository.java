package com.chorus.observe.persistence;

import com.chorus.observe.model.PromptAbTest;
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
 * JDBC repository for prompt A/B tests.
 */
public class PromptAbTestRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<PromptAbTest> rowMapper;

    public PromptAbTestRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new PromptAbTestRowMapper(mapper);
    }

    public void save(@NonNull PromptAbTest test) {
        String sql = """
            INSERT INTO prompt_ab_tests (test_id, dataset_id, prompt_a_id, prompt_b_id, status, winner_id, p_value, summary_metrics, created_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (test_id) DO UPDATE SET
                dataset_id = EXCLUDED.dataset_id,
                prompt_a_id = EXCLUDED.prompt_a_id,
                prompt_b_id = EXCLUDED.prompt_b_id,
                status = EXCLUDED.status,
                winner_id = EXCLUDED.winner_id,
                p_value = EXCLUDED.p_value,
                summary_metrics = EXCLUDED.summary_metrics,
                finished_at = EXCLUDED.finished_at
            """;
        jdbc.update(sql,
            test.testId(), test.datasetId(), test.promptAId(), test.promptBId(),
            test.status().name(), test.winnerId(), test.pValue(),
            toJson(test.summaryMetrics()), Timestamp.from(test.createdAt()),
            test.finishedAt() != null ? Timestamp.from(test.finishedAt()) : null
        );
    }

    public @NonNull Optional<PromptAbTest> findById(@NonNull String testId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM prompt_ab_tests WHERE test_id = ?", rowMapper, testId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<PromptAbTest> findAll() {
        return jdbc.query("SELECT * FROM prompt_ab_tests ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<PromptAbTest> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM prompt_ab_tests ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_ab_tests", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<PromptAbTest> findByStatus(PromptAbTest.Status status) {
        return jdbc.query("SELECT * FROM prompt_ab_tests WHERE status = ? ORDER BY created_at DESC", rowMapper, status.name());
    }

    public @NonNull List<PromptAbTest> findByStatus(PromptAbTest.Status status, int limit, int offset) {
        return jdbc.query("SELECT * FROM prompt_ab_tests WHERE status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, status.name(), limit, offset);
    }

    public long countByStatus(PromptAbTest.Status status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_ab_tests WHERE status = ?", Long.class, status.name());
        return count != null ? count : 0L;
    }

    public void deleteById(@NonNull String testId) {
        jdbc.update("DELETE FROM prompt_ab_tests WHERE test_id = ?", testId);
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class PromptAbTestRowMapper implements RowMapper<PromptAbTest> {
        private final ObjectMapper mapper;

        PromptAbTestRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public PromptAbTest mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new PromptAbTest(
                    rs.getString("test_id"),
                    rs.getString("dataset_id"),
                    rs.getString("prompt_a_id"),
                    rs.getString("prompt_b_id"),
                    PromptAbTest.Status.valueOf(rs.getString("status")),
                    rs.getString("winner_id"),
                    rs.getObject("p_value", Double.class),
                    mapper.readValue(rs.getString("summary_metrics"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
