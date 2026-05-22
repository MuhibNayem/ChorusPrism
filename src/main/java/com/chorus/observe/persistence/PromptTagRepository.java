package com.chorus.observe.persistence;

import com.chorus.observe.model.PromptTag;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Objects;

/**
 * JDBC repository for prompt tags.
 */
public class PromptTagRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<PromptTag> rowMapper = new PromptTagRowMapper();

    public PromptTagRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull PromptTag tag) {
        String sql = """
            INSERT INTO prompt_tags (version_id, tag_name, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT (version_id, tag_name) DO UPDATE SET
                created_at = EXCLUDED.created_at
            """;
        jdbc.update(sql, tag.versionId(), tag.tagName(), Timestamp.from(tag.createdAt()));
    }

    public void delete(@NonNull String versionId, @NonNull String tagName) {
        jdbc.update("DELETE FROM prompt_tags WHERE version_id = ? AND tag_name = ?", versionId, tagName);
    }

    public void deleteByVersionId(@NonNull String versionId) {
        jdbc.update("DELETE FROM prompt_tags WHERE version_id = ?", versionId);
    }

    public @NonNull List<PromptTag> findByVersionId(@NonNull String versionId) {
        return jdbc.query("SELECT * FROM prompt_tags WHERE version_id = ? ORDER BY tag_name", rowMapper, versionId);
    }

    public @NonNull List<PromptTag> findByVersionId(@NonNull String versionId, int limit, int offset) {
        return jdbc.query("SELECT * FROM prompt_tags WHERE version_id = ? ORDER BY tag_name LIMIT ? OFFSET ?", rowMapper, versionId, limit, offset);
    }

    public long countByVersionId(@NonNull String versionId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_tags WHERE version_id = ?", Long.class, versionId);
        return count != null ? count : 0L;
    }

    public @NonNull List<PromptTag> findByTagName(@NonNull String tagName) {
        return jdbc.query("SELECT * FROM prompt_tags WHERE tag_name = ? ORDER BY created_at DESC", rowMapper, tagName);
    }

    public @NonNull List<PromptTag> findByTagName(@NonNull String tagName, int limit, int offset) {
        return jdbc.query("SELECT * FROM prompt_tags WHERE tag_name = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tagName, limit, offset);
    }

    public long countByTagName(@NonNull String tagName) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_tags WHERE tag_name = ?", Long.class, tagName);
        return count != null ? count : 0L;
    }

    public @NonNull Optional<PromptTag> findByVersionAndTag(@NonNull String versionId, @NonNull String tagName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM prompt_tags WHERE version_id = ? AND tag_name = ?", rowMapper, versionId, tagName));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<PromptTag> findAll() {
        return jdbc.query("SELECT * FROM prompt_tags ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<PromptTag> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM prompt_tags ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM prompt_tags", Long.class);
        return count != null ? count : 0L;
    }

    private static final class PromptTagRowMapper implements RowMapper<PromptTag> {
        @Override
        public PromptTag mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PromptTag(
                rs.getString("version_id"),
                rs.getString("tag_name"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
