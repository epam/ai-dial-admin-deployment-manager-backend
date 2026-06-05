package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Summary of a latency-style metric. Percentiles are approximated from cumulative Prometheus
 * histogram buckets in a single snapshot (lifetime window).
 */
public record DistributionSummary(Double mean, Double p50, Double p95, Double p99, long count) {
}
