package com.chorus.observe.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Health indicator for Chorus Observe Server.
 * Reports database connectivity and basic subsystem status.
 */
@Component
public class ChorusObserveHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public ChorusObserveHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        if (dataSource == null) {
            return Health.down().withDetail("database", "no DataSource configured").build();
        }

        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(5);
            if (valid) {
                var meta = conn.getMetaData();
                return Health.up()
                    .withDetail("database", "connected")
                    .withDetail("databaseUrl", meta.getURL())
                    .withDetail("databaseProduct", meta.getDatabaseProductName())
                    .build();
            } else {
                return Health.down().withDetail("database", "connection invalid").build();
            }
        } catch (SQLException e) {
            return Health.down()
                .withDetail("database", "connection failed")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
