package com.chorus.observe.persistence;

import com.chorus.observe.model.AlertRuleChannel;
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

public class AlertRuleChannelRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<AlertRuleChannel> rowMapper = new AlertRuleChannelRowMapper();

    public AlertRuleChannelRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull AlertRuleChannel link) {
        jdbc.update(
            "INSERT INTO alert_rule_channels (rule_id, channel_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            link.ruleId(), link.channelId(), Timestamp.from(link.createdAt()));
    }

    public @NonNull List<AlertRuleChannel> findByRuleId(@NonNull String ruleId) {
        return jdbc.query("SELECT * FROM alert_rule_channels WHERE rule_id = ?", rowMapper, ruleId);
    }

    public @NonNull List<AlertRuleChannel> findByChannelId(@NonNull String channelId) {
        return jdbc.query("SELECT * FROM alert_rule_channels WHERE channel_id = ?", rowMapper, channelId);
    }

    public void deleteByRuleId(@NonNull String ruleId) {
        jdbc.update("DELETE FROM alert_rule_channels WHERE rule_id = ?", ruleId);
    }

    public void deleteByRuleIdAndChannelId(@NonNull String ruleId, @NonNull String channelId) {
        jdbc.update("DELETE FROM alert_rule_channels WHERE rule_id = ? AND channel_id = ?", ruleId, channelId);
    }

    public void deleteByChannelId(@NonNull String channelId) {
        jdbc.update("DELETE FROM alert_rule_channels WHERE channel_id = ?", channelId);
    }

    private static final class AlertRuleChannelRowMapper implements RowMapper<AlertRuleChannel> {
        @Override
        public AlertRuleChannel mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AlertRuleChannel(
                rs.getString("rule_id"),
                rs.getString("channel_id"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
