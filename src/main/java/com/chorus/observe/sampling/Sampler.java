package com.chorus.observe.sampling;

import org.jspecify.annotations.NonNull;

/**
 * Sampling strategy for trace ingestion.
 * <p>
 * Sampling decisions can be made at different points:
 * <ul>
 *   <li><b>Head-based:</b> decide at trace start, apply to all spans in the trace</li>
 *   <li><b>Random:</b> independent probabilistic decision per trace</li>
 *   <li><b>Tail-based:</b> decide after trace completion based on attributes (e.g., errors)</li>
 * </ul>
 */
public interface Sampler {

    /**
     * Determine whether a trace should be sampled (kept).
     *
     * @param traceId the trace identifier
     * @return true if the trace should be ingested and stored
     */
    boolean shouldSample(@NonNull String traceId);

    /**
     * Determine whether a trace should be sampled based on trace attributes.
     * Used for tail-based sampling after the trace is complete.
     *
     * @param traceId   the trace identifier
     * @param hasError  whether the trace contains errors
     * @param latencyMs total trace latency in milliseconds
     * @return true if the trace should be ingested and stored
     */
    default boolean shouldSample(@NonNull String traceId, boolean hasError, long latencyMs) {
        return shouldSample(traceId);
    }
}
