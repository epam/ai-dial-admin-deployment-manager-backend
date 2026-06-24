package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Operational block of the unified schema. Counter-derived ratios are lifetime aggregates.
 */
public record OperationalMetrics(Double requestErrorRatio, DistributionSummary e2eLatency) {
}
