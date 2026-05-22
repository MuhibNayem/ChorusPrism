package com.chorus.observe.persistence;

import com.chorus.observe.model.NotificationChannel;
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

public class NotificationChannelRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<NotificationChannel> rowMapper;

    public NotificationChannelRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new NotificationChannelRowMapper(mapper);
    }

    public void save(@NonNull NotificationChannel channel) {
        String sql = """
            INSERT INTO notification_channels (channel_id, tenant_id, name, channel_type, config, enabled, last_used_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (channel_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                channel_type = EXCLUDED.channel_type,
                config = EXCLUDED.config,
                enabled = EXCLUDED.enabled,
                last_used_at = EXCLUDED.last_used_at,
                updated_at = EXCLUDED.updated_at
            """;
        try {
            jdbc.update(sql,
                channel.channelId(), channel.tenantId(), channel.name(),
                channel.channelType().name(),
                mapper.writeValueAsString(channel.config()),
                channel.enabled(),
                Timestamp.from(channel.lastUsedAt()),
                Timestamp.from(channel.createdAt()),
                Timestamp.from(channel.updatedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize config", e);
        }
    }

    public @NonNull Optional<NotificationChannel> findById(@NonNull String channelId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM notification_channels WHERE channel_id = ?", rowMapper, channelId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<NotificationChannel> findByTenant(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM notification_channels WHERE tenant_id = ? ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public @NonNull List<NotificationChannel> findEnabledByTenant(@NonNull String tenantId) {
        return jdbc.query("SELECT * FROM notification_channels WHERE tenant_id = ? AND enabled = TRUE ORDER BY created_at DESC", rowMapper, tenantId);
    }

    public void deleteById(@NonNull String channelId) {
        jdbc.update("DELETE FROM notification_channels WHERE channel_id = ?", channelId);
    }

    private static final class NotificationChannelRowMapper implements RowMapper<NotificationChannel> {
        private final ObjectMapper mapper;

        NotificationChannelRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public NotificationChannel mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new NotificationChannel(
                    rs.getString("channel_id"),
                    rs.getString("tenant_id"),
                    rs.getString("name"),
                    NotificationChannel.ChannelType.valueOf(rs.getString("channel_type")),
                    mapper.readValue(rs.getString("config"), new TypeReference<Map<String, Object>>() {}),
                    rs.getBoolean("enabled"),
                    rs.getTimestamp("last_used_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
