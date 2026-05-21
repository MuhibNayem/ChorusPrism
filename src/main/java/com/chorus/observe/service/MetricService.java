package com.chorus.observe.service;

import com.chorus.observe.model.MetricSnapshot;
import com.chorus.observe.persistence.MetricRepository;
import com.chorus.observe.persistence.MetricRepository.MetricAggregate;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Service layer for metric queries and aggregations.
 */
public class MetricService {

    private final MetricRepository metricRepository;

    public MetricService(@NonNull MetricRepository metricRepository) {
        this.metricRepository = Objects.requireNonNull(metricRepository);
    }

    public @NonNull List<MetricSnapshot> getMetrics(
            @NonNull String metricName, @NonNull Instant from, @NonNull Instant to, int limit) {
        return metricRepository.findByNameAndTimeRange(metricName, from, to, limit);
    }

    public @NonNull List<MetricAggregate> aggregateByHour(
            @NonNull String metricName, @NonNull Instant from, @NonNull Instant to) {
        return metricRepository.aggregateByHour(metricName, from, to);
    }
}
