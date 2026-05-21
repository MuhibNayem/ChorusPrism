package com.chorus.observe.service;

import com.chorus.observe.model.Span;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory pub/sub for real-time span streaming via SSE.
 * <p>
 * When spans are ingested for a run, all subscribed SSE emitters receive
 * the span as a Server-Sent Event. Emitters auto-expire after 5 minutes.
 */
public class SpanStreamService {

    private static final Logger LOG = LoggerFactory.getLogger(SpanStreamService.class);

    private final Map<String, List<SseEmitter>> subscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public SpanStreamService(@NonNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public void subscribe(@NonNull String runId, @NonNull SseEmitter emitter) {
        subscriptions.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        LOG.debug("SSE subscriber added for run {}", runId);
    }

    public void unsubscribe(@NonNull String runId, @NonNull SseEmitter emitter) {
        List<SseEmitter> list = subscriptions.get(runId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscriptions.remove(runId);
            }
        }
    }

    public void publish(@NonNull String runId, @NonNull Span span) {
        List<SseEmitter> list = subscriptions.get(runId);
        if (list == null || list.isEmpty()) return;

        try {
            String json = mapper.writeValueAsString(span);
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("span")
                .data(json);

            for (SseEmitter emitter : list) {
                try {
                    emitter.send(event);
                } catch (IOException e) {
                    unsubscribe(runId, emitter);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to publish span event for run {}", runId, e);
        }
    }
}
