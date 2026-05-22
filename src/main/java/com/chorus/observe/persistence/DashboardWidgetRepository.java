package com.chorus.observe.persistence;

import com.chorus.observe.model.DashboardWidget;
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

public class DashboardWidgetRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<DashboardWidget> rowMapper;

    public DashboardWidgetRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new DashboardWidgetRowMapper(mapper);
    }

    public void save(@NonNull DashboardWidget widget) {
        String sql = """
            INSERT INTO dashboard_widgets (widget_id, dashboard_id, widget_type, title, query_config, position, refresh_seconds, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
            ON CONFLICT (widget_id) DO UPDATE SET
                dashboard_id = EXCLUDED.dashboard_id,
                widget_type = EXCLUDED.widget_type,
                title = EXCLUDED.title,
                query_config = EXCLUDED.query_config,
                position = EXCLUDED.position,
                refresh_seconds = EXCLUDED.refresh_seconds,
                updated_at = EXCLUDED.updated_at
            """;
        try {
            jdbc.update(sql,
                widget.widgetId(), widget.dashboardId(), widget.widgetType().name(),
                widget.title(),
                mapper.writeValueAsString(widget.queryConfig()),
                mapper.writeValueAsString(widget.position()),
                widget.refreshSeconds(),
                Timestamp.from(widget.createdAt()),
                Timestamp.from(widget.updatedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSON", e);
        }
    }

    public @NonNull Optional<DashboardWidget> findById(@NonNull String widgetId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM dashboard_widgets WHERE widget_id = ?", rowMapper, widgetId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<DashboardWidget> findByDashboardId(@NonNull String dashboardId) {
        return jdbc.query("SELECT * FROM dashboard_widgets WHERE dashboard_id = ? ORDER BY created_at ASC", rowMapper, dashboardId);
    }

    public void deleteById(@NonNull String widgetId) {
        jdbc.update("DELETE FROM dashboard_widgets WHERE widget_id = ?", widgetId);
    }

    public void deleteByDashboardId(@NonNull String dashboardId) {
        jdbc.update("DELETE FROM dashboard_widgets WHERE dashboard_id = ?", dashboardId);
    }

    private static final class DashboardWidgetRowMapper implements RowMapper<DashboardWidget> {
        private final ObjectMapper mapper;

        DashboardWidgetRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public DashboardWidget mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new DashboardWidget(
                    rs.getString("widget_id"),
                    rs.getString("dashboard_id"),
                    DashboardWidget.WidgetType.valueOf(rs.getString("widget_type")),
                    rs.getString("title"),
                    mapper.readValue(rs.getString("query_config"), new TypeReference<Map<String, Object>>() {}),
                    mapper.readValue(rs.getString("position"), new TypeReference<Map<String, Object>>() {}),
                    rs.getInt("refresh_seconds"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
