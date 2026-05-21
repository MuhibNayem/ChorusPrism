package com.chorus.observe.persistence;

import com.chorus.observe.model.MetricSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
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

/**
 * JDBC repository for metric snapshots.
 */
public class MetricRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<MetricSnapshot> rowMapper;

    public MetricRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new MetricRowMapper(mapper);
    }

    public void save(@NonNull MetricSnapshot snapshot) {
        String sql = """
            INSERT INTO metric_snapshots (snapshot_id, metric_name, value, tags, timestamp)
            VALUES (?, ?, ?, ?::jsonb, ?)
            """;
        jdbc.update(sql,
            snapshot.snapshotId(), snapshot.metricName(), snapshot.value(),
            toJson(snapshot.tags()), Timestamp.from(snapshot.timestamp())
        );
    }

    public @NonNull List<MetricSnapshot> findByNameAndTimeRange(
            @NonNull String metricName, @NonNull Instant from, @NonNull Instant to, int limit) {
        return jdbc.query(
            "SELECT * FROM metric_snapshots WHERE metric_name = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC LIMIT ?",
            rowMapper, metricName, Timestamp.from(from), Timestamp.from(to), limit);
    }

    public @NonNull List<MetricAggregate> aggregateByHour(
            @NonNull String metricName, @NonNull Instant from, @NonNull Instant to) {
        String sql = """
            SELECT
                date_trunc('hour', timestamp) AS bucket,
                AVG(value) AS avg_value,
                MIN(value) AS min_value,
                MAX(value) AS max_value,
                COUNT(*) AS count
            FROM metric_snapshots
            WHERE metric_name = ? AND timestamp >= ? AND timestamp <= ?
            GROUP BY date_trunc('hour', timestamp)
            ORDER BY bucket DESC
            """;
        return jdbc.query(sql, (rs, rowNum) -> new MetricAggregate(
            rs.getTimestamp("bucket").toInstant(),
            rs.getDouble("avg_value"),
            rs.getDouble("min_value"),
            rs.getDouble("max_value"),
            rs.getLong("count")
        ), metricName, Timestamp.from(from), Timestamp.from(to));
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    public record MetricAggregate(
        @NonNull Instant bucket,
        double avg,
        double min,
        double max,
        long count
    ) {}

    private static final class MetricRowMapper implements RowMapper<MetricSnapshot> {
        private final ObjectMapper mapper;

        MetricRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public MetricSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new MetricSnapshot(
                    rs.getString("snapshot_id"),
                    rs.getString("metric_name"),
                    rs.getDouble("value"),
                    mapper.readValue(rs.getString("tags"), new TypeReference<Map<String, String>>() {}),
                    rs.getTimestamp("timestamp").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
