package com.epam.aidial.deployment.manager.web.dto.metrics;

/**
 * Latency distribution summary. Percentiles are approximated from cumulative Prometheus
 * histogram buckets in a single snapshot (lifetime window).
 */
public record DistributionSummaryDto(Double mean, Double p50, Double p95, Double p99, long count) {
}
