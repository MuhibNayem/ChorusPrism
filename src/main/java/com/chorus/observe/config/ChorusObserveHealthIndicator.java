package com.chorus.observe.config;

import com.chorus.observe.store.SpanStore;
import io.grpc.Server;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composite health indicator for Chorus Observe Server.
 * Reports health of PostgreSQL, ClickHouse (via SpanStore), and gRPC server.
 */
@Component
public class ChorusObserveHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final SpanStore spanStore;
    private final ObjectProvider<Server> grpcServerProvider;

    public ChorusObserveHealthIndicator(@NonNull DataSource dataSource,
                                        @NonNull SpanStore spanStore,
                                        @NonNull ObjectProvider<Server> grpcServerProvider) {
        this.dataSource = dataSource;
        this.spanStore = spanStore;
        this.grpcServerProvider = grpcServerProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean allUp = true;

        // PostgreSQL health
        var pg = checkPostgres();
        details.put("postgresql", pg);
        if (!pg.up) allUp = false;

        // SpanStore health (covers ClickHouse or Postgres depending on config)
        var store = checkSpanStore();
        details.put("spanStore", store);
        if (!store.up) allUp = false;

        // gRPC server health
        var grpc = checkGrpc();
        details.put("grpc", grpc);
        if (!grpc.up) allUp = false;

        if (allUp) {
            return Health.up().withDetails(details).build();
        }
        return Health.down().withDetails(details).build();
    }

    private record SubsystemHealth(boolean up, Map<String, Object> details) {}

    private SubsystemHealth checkPostgres() {
        Map<String, Object> details = new LinkedHashMap<>();
        if (dataSource == null) {
            details.put("status", "no DataSource configured");
            return new SubsystemHealth(false, details);
        }
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                var meta = conn.getMetaData();
                details.put("status", "connected");
                details.put("url", meta.getURL());
                details.put("product", meta.getDatabaseProductName());
                return new SubsystemHealth(true, details);
            }
            details.put("status", "connection invalid");
            return new SubsystemHealth(false, details);
        } catch (SQLException e) {
            details.put("status", "connection failed");
            details.put("error", e.getMessage());
            return new SubsystemHealth(false, details);
        }
    }

    private SubsystemHealth checkSpanStore() {
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            boolean healthy = spanStore.isHealthy();
            details.put("status", healthy ? "healthy" : "unhealthy");
            return new SubsystemHealth(healthy, details);
        } catch (Exception e) {
            details.put("status", "check failed");
            details.put("error", e.getMessage());
            return new SubsystemHealth(false, details);
        }
    }

    private SubsystemHealth checkGrpc() {
        Map<String, Object> details = new LinkedHashMap<>();
        Server server = grpcServerProvider.getIfAvailable();
        if (server == null) {
            details.put("status", "not configured");
            return new SubsystemHealth(true, details);
        }
        if (server.isShutdown() || server.isTerminated()) {
            details.put("status", "shut down");
            return new SubsystemHealth(false, details);
        }
        details.put("status", "running");
        details.put("port", server.getPort());
        return new SubsystemHealth(true, details);
    }
}
