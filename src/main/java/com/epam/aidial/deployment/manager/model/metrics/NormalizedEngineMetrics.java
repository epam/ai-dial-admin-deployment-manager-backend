package com.epam.aidial.deployment.manager.model.metrics;

import java.util.Map;

/**
 * Output of an engine metrics normalizer — the engine-derived subset of the snapshot.
 * The service assembles it (plus replicas, pod usage, availability, timestamps) into
 * {@link UnifiedDeploymentMetrics}. {@code rawCounters} carries only directly-exposed
 * cumulative counters under unified names; derived values are never echoed as raw.
 */
public record NormalizedEngineMetrics(
        ServingMetrics serving,
        OperationalMetrics operational,
        Map<String, Double> rawCounters
) {
}
