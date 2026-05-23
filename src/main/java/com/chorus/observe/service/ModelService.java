package com.chorus.observe.service;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * Service layer for model usage aggregations.
 */
public class ModelService {

    private final JdbcTemplate jdbc;

    public ModelService(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    public @NonNull List<ModelMetrics> getModels() {
        return jdbc.query(
            """
            SELECT model,
                   COUNT(*) as runs,
                   COALESCE(SUM(total_tokens), 0) as tokens,
                   COALESCE(SUM(total_cost), 0) as cost
            FROM runs
            WHERE model IS NOT NULL
            GROUP BY model
            ORDER BY runs DESC
            """,
            (rs, rowNum) -> new ModelMetrics(
                rs.getString("model"),
                DashboardService.inferProvider(rs.getString("model")),
                rs.getLong("runs"),
                rs.getLong("tokens"),
                rs.getBigDecimal("cost").doubleValue()
            )
        );
    }

    public record ModelMetrics(
        @NonNull String model,
        @NonNull String provider,
        long runs,
        long tokens,
        double cost
    ) {}
}
