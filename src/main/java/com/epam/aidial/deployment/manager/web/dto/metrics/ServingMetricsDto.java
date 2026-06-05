package com.epam.aidial.deployment.manager.web.dto.metrics;

/** Serving-quality block of the unified metrics schema; absent engine metrics are {@code null}. */
public record ServingMetricsDto(
        DistributionSummaryDto ttft,
        DistributionSummaryDto interTokenLatency,
        TokensPerSecondDto tokensPerSecond,
        Integer queueDepth,
        Integer runningRequests,
        Double kvCacheUsage
) {

    /** Lifetime token throughput, tokens/second. */
    public record TokensPerSecondDto(Double prompt, Double generation) {
    }
}
