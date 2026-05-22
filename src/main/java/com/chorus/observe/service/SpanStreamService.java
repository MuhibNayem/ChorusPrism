package com.chorus.observe.service;

import com.chorus.observe.model.Span;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;

/**
 * In-memory pub/sub for real-time span streaming via SSE.
 * <p>
 * Safety limits:
 * <ul>
 *   <li>Max 100 subscribers per run ID.</li>
 *   <li>Emitters older than 6 minutes are auto-evicted (SSE timeout is 5 min).</li>
 *   <li>Failed sends remove the emitter immediately.</li>
 * </ul>
 */
public class SpanStreamService {

    private static final Logger LOG = LoggerFactory.getLogger(SpanStreamService.class);
    private static final int MAX_SUBSCRIBERS_PER_RUN = 100;
    private static final int EMITTER_TTL_MINUTES = 6;
    private static final int CLEANUP_INTERVAL_SECONDS = 30;

    private final Map<String, List<Subscriber>> subscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final ScheduledExecutorService cleanupScheduler;

    public SpanStreamService(@NonNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chorus-observe-sse-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(
            this::evictStaleSubscribers, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void subscribe(@NonNull String runId, @NonNull SseEmitter emitter) {
        List<Subscriber> list = subscriptions.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());

        synchronized (list) {
            if (list.size() >= MAX_SUBSCRIBERS_PER_RUN) {
                LOG.warn("Max subscribers ({}) reached for run {}, rejecting new subscription", MAX_SUBSCRIBERS_PER_RUN, runId);
                emitter.completeWithError(new IllegalStateException(
                    "Max subscribers reached for this run. Please retry later."));
                return;
            }
            list.add(new Subscriber(emitter, Instant.now()));
        }
        LOG.debug("SSE subscriber added for run {} (total: {})", runId, list.size());
    }

    public void unsubscribe(@NonNull String runId, @NonNull SseEmitter emitter) {
        List<Subscriber> list = subscriptions.get(runId);
        if (list != null) {
            list.removeIf(s -> s.emitter == emitter);
            if (list.isEmpty()) {
                subscriptions.remove(runId);
            }
        }
    }

    public void publish(@NonNull String runId, @NonNull Span span) {
        List<Subscriber> list = subscriptions.get(runId);
        if (list == null || list.isEmpty()) return;

        try {
            String json = mapper.writeValueAsString(span);
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("span")
                .data(json);

            for (Subscriber sub : list) {
                try {
                    sub.emitter.send(event);
                    sub.lastActivity = Instant.now();
                } catch (IOException | IllegalStateException e) {
                    unsubscribe(runId, sub.emitter);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to publish span event for run {}", runId, e);
        }
    }

    private void evictStaleSubscribers() {
        try {
            Instant cutoff = Instant.now().minusSeconds(EMITTER_TTL_MINUTES * 60L);
            for (Map.Entry<String, List<Subscriber>> entry : subscriptions.entrySet()) {
                List<Subscriber> list = entry.getValue();
                boolean removed = list.removeIf(sub -> {
                    if (sub.createdAt.isBefore(cutoff)) {
                        try {
                            sub.emitter.complete();
                        } catch (Exception ignored) {}
                        return true;
                    }
                    return false;
                });
                if (removed) {
                    LOG.debug("Evicted stale SSE subscribers for run {}", entry.getKey());
                }
                if (list.isEmpty()) {
                    subscriptions.remove(entry.getKey());
                }
            }
        } catch (Exception e) {
            LOG.warn("SSE cleanup task failed", e);
        }
    }

    private static final class Subscriber {
        final SseEmitter emitter;
        final Instant createdAt;
        volatile Instant lastActivity;

        Subscriber(SseEmitter emitter, Instant createdAt) {
            this.emitter = emitter;
            this.createdAt = createdAt;
            this.lastActivity = createdAt;
        }
    }

    @PreDestroy
    public void close() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
