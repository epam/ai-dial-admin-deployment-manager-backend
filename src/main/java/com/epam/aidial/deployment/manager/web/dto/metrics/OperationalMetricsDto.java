package com.epam.aidial.deployment.manager.web.dto.metrics;

/** Operational block of the unified metrics schema; ratios are lifetime aggregates. */
public record OperationalMetricsDto(Double requestErrorRatio, DistributionSummaryDto e2eLatency) {
}
