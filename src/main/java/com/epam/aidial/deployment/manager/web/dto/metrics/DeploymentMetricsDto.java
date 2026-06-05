package com.epam.aidial.deployment.manager.web.dto.metrics;

import java.time.Instant;
import java.util.Map;

/**
 * Live metrics snapshot for a model deployment — the unified, engine-neutral contract.
 * Degraded blocks are {@code null} with the reason recorded in {@code availability};
 * counter-derived values are lifetime aggregates ({@code window: "lifetime"}) and the raw
 * cumulative counters are echoed in {@code rawCounters} for client-side rate derivation.
 */
public record DeploymentMetricsDto(
        Instant collectedAt,
        String engine,
        String scrapedPod,
        String window,
        Map<String, MetricsAvailabilityDto> availability,
        ServingMetricsDto serving,
        ResourceMetricsDto resources,
        OperationalMetricsDto operational,
        Map<String, Double> rawCounters
) {
}
