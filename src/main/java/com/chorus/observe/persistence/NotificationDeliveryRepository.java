package com.chorus.observe.persistence;

import com.chorus.observe.model.NotificationDelivery;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * JDBC repository for notification delivery audit records.
 * Uses UPSERT so callers can update status in-place without tracking whether
 * the row was previously inserted.
 */
public class NotificationDeliveryRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<NotificationDelivery> rowMapper = new DeliveryRowMapper();

    public NotificationDeliveryRepository(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    /**
     * Inserts or updates a delivery record (UPSERT on delivery_id).
     */
    public void save(@NonNull NotificationDelivery delivery) {
        String sql = """
            INSERT INTO notification_deliveries
                (delivery_id, event_id, channel_id, status, attempt_count, last_error, sent_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (delivery_id) DO UPDATE SET
                status        = EXCLUDED.status,
                attempt_count = EXCLUDED.attempt_count,
                last_error    = EXCLUDED.last_error,
                sent_at       = EXCLUDED.sent_at,
                updated_at    = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            delivery.deliveryId(),
            delivery.eventId(),
            delivery.channelId(),
            delivery.status().toDbValue(),
            delivery.attemptCount(),
            delivery.lastError(),
            delivery.sentAt() != null ? Timestamp.from(delivery.sentAt()) : null,
            Timestamp.from(delivery.createdAt()),
            Timestamp.from(delivery.updatedAt()));
    }

    public @NonNull List<NotificationDelivery> findByEventId(@NonNull String eventId) {
        return jdbc.query(
            "SELECT * FROM notification_deliveries WHERE event_id = ? ORDER BY created_at DESC",
            rowMapper, eventId);
    }

    public @NonNull List<NotificationDelivery> findByChannelId(@NonNull String channelId, int limit) {
        return jdbc.query(
            "SELECT * FROM notification_deliveries WHERE channel_id = ? ORDER BY created_at DESC LIMIT ?",
            rowMapper, channelId, limit);
    }

    public @NonNull List<NotificationDelivery> findDlq(int limit) {
        return jdbc.query(
            "SELECT * FROM notification_deliveries WHERE status = 'dlq' ORDER BY created_at DESC LIMIT ?",
            rowMapper, limit);
    }

    public long countByStatus(@NonNull String status) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification_deliveries WHERE status = ?",
            Long.class, status.toLowerCase());
        return count != null ? count : 0L;
    }

    private static final class DeliveryRowMapper implements RowMapper<NotificationDelivery> {
        @Override
        public NotificationDelivery mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp sentAtTs = rs.getTimestamp("sent_at");
            @Nullable Instant sentAt = sentAtTs != null ? sentAtTs.toInstant() : null;
            return new NotificationDelivery(
                rs.getString("delivery_id"),
                rs.getString("event_id"),
                rs.getString("channel_id"),
                NotificationDelivery.Status.fromDbValue(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getString("last_error"),
                sentAt,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
