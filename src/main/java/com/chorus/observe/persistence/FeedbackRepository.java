package com.chorus.observe.persistence;

import com.chorus.observe.model.Feedback;
import org.jspecify.annotations.NonNull;
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
 * JDBC repository for feedback.
 */
public class FeedbackRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Feedback> rowMapper = new FeedbackRowMapper();

    public FeedbackRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull Feedback feedback) {
        String sql = """
            INSERT INTO feedback (feedback_id, run_id, span_id, score, label, comment, source, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (feedback_id) DO UPDATE SET
                run_id = EXCLUDED.run_id,
                span_id = EXCLUDED.span_id,
                score = EXCLUDED.score,
                label = EXCLUDED.label,
                comment = EXCLUDED.comment,
                source = EXCLUDED.source,
                created_at = EXCLUDED.created_at
            """;
        jdbc.update(sql,
            feedback.feedbackId(), feedback.runId(), feedback.spanId(),
            feedback.score(), feedback.label(), feedback.comment(),
            feedback.source(), Timestamp.from(feedback.createdAt())
        );
    }

    public @NonNull List<Feedback> findByRunId(@NonNull String runId) {
        return jdbc.query(
            "SELECT * FROM feedback WHERE run_id = ? ORDER BY created_at DESC",
            rowMapper, runId);
    }

    public @NonNull List<Feedback> findByRunId(@NonNull String runId, int limit, int offset) {
        return jdbc.query(
            "SELECT * FROM feedback WHERE run_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper, runId, limit, offset);
    }

    public long countByRunId(@NonNull String runId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM feedback WHERE run_id = ?", Long.class, runId);
        return count != null ? count : 0L;
    }

    public @NonNull Optional<Feedback> findById(@NonNull String feedbackId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM feedback WHERE feedback_id = ?", rowMapper, feedbackId));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static final class FeedbackRowMapper implements RowMapper<Feedback> {
        @Override
        public Feedback mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Feedback(
                rs.getString("feedback_id"),
                rs.getString("run_id"),
                rs.getString("span_id"),
                rs.getObject("score", Double.class),
                rs.getString("label"),
                rs.getString("comment"),
                rs.getString("source"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
