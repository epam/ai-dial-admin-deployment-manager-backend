package com.epam.aidial.deployment.manager.model.metrics;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

/**
 * Layer-neutral live metrics snapshot for a model deployment — the unified, engine-neutral
 * schema. Counter-derived values are lifetime aggregates (since engine process start),
 * labelled via {@code window}; raw cumulative counters are echoed in {@code rawCounters} so
 * clients can derive their own rates over the interval between their polls (subject to counter
 * resets). Arbitrary historical windows require the time-series backend (ADR Option C). Because
 * responses are served from a short-TTL cache, clients must use {@code collectedAt} as the rate
 * time base and dedupe identical snapshots: consecutive responses within the TTL are byte-identical
 * and represent a single data point, so effective rate resolution is bounded by the cache TTL.
 */
@Builder
public record UnifiedDeploymentMetrics(
        Instant collectedAt,
        EngineFamily engine,
        String scrapedPod,
        String window,
        Map<String, AvailabilityStatus> availability,
        ServingMetrics serving,
        ResourceMetrics resources,
        OperationalMetrics operational,
        Map<String, Double> rawCounters
) {

    /** The only aggregation window supported by the PoC. */
    public static final String WINDOW_LIFETIME = "lifetime";

    /** Availability map keys — one entry per block on every response. */
    public static final String AVAILABILITY_SERVING = "serving";
    public static final String AVAILABILITY_RESOURCES = "resources";
    public static final String AVAILABILITY_OPERATIONAL = "operational";
    public static final String AVAILABILITY_RESOURCES_USAGE = "resources.usage";
    public static final String AVAILABILITY_RESOURCES_GPU = "resources.gpu";
}
