package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Serving-quality block of the unified schema. Absent engine metrics yield {@code null} fields
 * while the block itself stays available.
 */
public record ServingMetrics(
        DistributionSummary ttft,
        DistributionSummary interTokenLatency,
        Double promptTokensPerSecond,
        Double generationTokensPerSecond,
        Integer queueDepth,
        Integer runningRequests,
        Double kvCacheUsage
) {
}
