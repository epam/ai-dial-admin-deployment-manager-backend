package com.epam.aidial.deployment.manager.web.dto.metrics;

/**
 * Operational block of the unified metrics schema; ratios are lifetime aggregates. Like the serving
 * block, values reflect the single scraped replica (the pod named in {@code scrapedPod}), not an
 * aggregate across replicas.
 */
public record OperationalMetricsDto(Double requestErrorRatio, DistributionSummaryDto e2eLatency) {
}
