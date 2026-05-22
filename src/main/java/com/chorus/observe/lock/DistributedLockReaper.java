package com.chorus.observe.lock;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * Background task that reaps expired distributed locks.
 * <p>
 * Runs every 60 seconds by default. Without this, a crashed JVM that held a
 * lock would leave the row in the database forever (until {@code tryStealExpired}
 * in {@link JdbcDistributedLock} kicks in during the next acquisition attempt).
 * The reaper makes expiry visible more quickly.
 */
public class DistributedLockReaper {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedLockReaper.class);

    private final DataSource dataSource;

    public DistributedLockReaper(@NonNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Scheduled(fixedRate = 60_000)
    public void reap() {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("DELETE FROM distributed_locks WHERE expires_at < ?")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                LOG.info("Reaped {} expired distributed lock(s)", deleted);
            }
        } catch (Exception e) {
            LOG.error("Distributed lock reaper failed", e);
        }
    }
}
