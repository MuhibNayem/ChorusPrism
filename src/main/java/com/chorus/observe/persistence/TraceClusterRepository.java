package com.chorus.observe.persistence;

import com.chorus.observe.model.TraceCluster;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository for trace clusters.
 */
public class TraceClusterRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<TraceCluster> rowMapper;

    public TraceClusterRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new TraceClusterRowMapper(mapper);
    }

    public void save(@NonNull TraceCluster cluster) {
        String sql = """
            INSERT INTO trace_clusters (cluster_id, label, description, run_count, avg_score, avg_cost, period_start, period_end, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (cluster_id) DO UPDATE SET
                label = EXCLUDED.label,
                description = EXCLUDED.description,
                run_count = EXCLUDED.run_count,
                avg_score = EXCLUDED.avg_score,
                avg_cost = EXCLUDED.avg_cost,
                period_start = EXCLUDED.period_start,
                period_end = EXCLUDED.period_end,
                metadata = EXCLUDED.metadata
            """;
        jdbc.update(sql,
            cluster.clusterId(), cluster.label(), cluster.description(),
            cluster.runCount(), cluster.avgScore(), cluster.avgCost(),
            Timestamp.from(cluster.periodStart()), Timestamp.from(cluster.periodEnd()),
            toJson(cluster.metadata()), Timestamp.from(cluster.createdAt())
        );
    }

    public @NonNull Optional<TraceCluster> findById(@NonNull String clusterId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM trace_clusters WHERE cluster_id = ?", rowMapper, clusterId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<TraceCluster> findAll() {
        return jdbc.query("SELECT * FROM trace_clusters ORDER BY created_at DESC", rowMapper);
    }

    public @NonNull List<TraceCluster> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM trace_clusters ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM trace_clusters", Long.class);
        return count != null ? count : 0L;
    }

    public @NonNull List<TraceCluster> findByPeriod(@NonNull Instant start, @NonNull Instant end) {
        return jdbc.query("SELECT * FROM trace_clusters WHERE period_start >= ? AND period_end <= ? ORDER BY run_count DESC",
            rowMapper, Timestamp.from(start), Timestamp.from(end));
    }

    public @NonNull List<TraceCluster> findByPeriod(@NonNull Instant start, @NonNull Instant end, int limit, int offset) {
        return jdbc.query("SELECT * FROM trace_clusters WHERE period_start >= ? AND period_end <= ? ORDER BY run_count DESC LIMIT ? OFFSET ?",
            rowMapper, Timestamp.from(start), Timestamp.from(end), limit, offset);
    }

    public long countByPeriod(@NonNull Instant start, @NonNull Instant end) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM trace_clusters WHERE period_start >= ? AND period_end <= ?", Long.class, Timestamp.from(start), Timestamp.from(end));
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static final class TraceClusterRowMapper implements RowMapper<TraceCluster> {
        private final ObjectMapper mapper;

        TraceClusterRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public TraceCluster mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new TraceCluster(
                    rs.getString("cluster_id"),
                    rs.getString("label"),
                    rs.getString("description"),
                    rs.getInt("run_count"),
                    rs.getObject("avg_score", Double.class),
                    rs.getBigDecimal("avg_cost"),
                    rs.getTimestamp("period_start").toInstant(),
                    rs.getTimestamp("period_end").toInstant(),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
