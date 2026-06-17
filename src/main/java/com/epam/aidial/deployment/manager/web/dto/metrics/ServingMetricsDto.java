package com.epam.aidial.deployment.manager.web.dto.metrics;

/**
 * Serving-quality block of the unified metrics schema; absent engine metrics are {@code null}.
 * Values reflect the single scraped replica (the pod named in {@code scrapedPod}), not an aggregate
 * across replicas — for {@code replicas > 1} the gauges (queue depth, running requests, KV-cache
 * usage) are single-replica figures.
 */
public record ServingMetricsDto(
        DistributionSummaryDto ttft,
        DistributionSummaryDto interTokenLatency,
        TokensPerSecondDto tokensPerSecond,
        Integer queueDepth,
        Integer runningRequests,
        Double kvCacheUsage,
        DistributionSummaryDto requestLatency,
        Double requestsPerSecond
) {

    /** Lifetime token throughput, tokens/second. */
    public record TokensPerSecondDto(Double prompt, Double generation) {
    }
}
