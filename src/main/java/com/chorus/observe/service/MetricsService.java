package com.chorus.observe.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Publishes custom application metrics via Micrometer.
 */
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final Counter evalRunsTotal;
    private final Timer evalRunsDuration;
    private final Counter redTeamRunsTotal;
    private final Counter ingestionSpansTotal;

    public MetricsService(@NonNull MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.evalRunsTotal = Counter.builder("eval.runs.total")
            .description("Total number of evaluation runs")
            .register(meterRegistry);
        this.evalRunsDuration = Timer.builder("eval.runs.duration")
            .description("Duration of evaluation runs")
            .register(meterRegistry);
        this.redTeamRunsTotal = Counter.builder("redteam.runs.total")
            .description("Total number of red team runs")
            .register(meterRegistry);
        this.ingestionSpansTotal = Counter.builder("ingestion.spans.total")
            .description("Total number of ingested spans")
            .register(meterRegistry);
    }

    public void incrementEvalRunsTotal() {
        evalRunsTotal.increment();
    }

    public void recordEvalRunDuration(long durationMs) {
        evalRunsDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void incrementRedTeamRunsTotal() {
        redTeamRunsTotal.increment();
    }

    public void incrementIngestionSpansTotal(double amount) {
        ingestionSpansTotal.increment(amount);
    }
}
