package com.chorus.observe.config;

import io.grpc.Server;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of the OTLP gRPC server.
 * Implements {@link SmartLifecycle} so Spring Boot calls
 * {@link #stop()} during graceful shutdown (SIGTERM).
 */
public final class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerLifecycle.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final Server server;
    private volatile boolean running = false;

    public GrpcServerLifecycle(@NonNull Server server) {
        this.server = server;
    }

    @Override
    public void start() {
        if (server.isShutdown() || server.isTerminated()) {
            LOG.warn("gRPC server already shut down, skipping start");
            return;
        }
        running = true;
        LOG.info("gRPC server lifecycle started (port={})", server.getPort());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        LOG.info("Shutting down gRPC server (timeout={}s)", SHUTDOWN_TIMEOUT_SECONDS);
        server.shutdown();
        try {
            if (!server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("gRPC server did not terminate gracefully within {}s, forcing shutdown", SHUTDOWN_TIMEOUT_SECONDS);
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
        }
        LOG.info("gRPC server shut down complete");
    }

    @Override
    public boolean isRunning() {
        return running && !server.isShutdown();
    }

    @Override
    public int getPhase() {
        // Shut down after web server (web server is Integer.MAX_VALUE - 1)
        return Integer.MAX_VALUE - 2;
    }
}
