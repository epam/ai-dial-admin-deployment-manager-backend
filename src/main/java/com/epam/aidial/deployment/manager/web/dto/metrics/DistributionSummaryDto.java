package com.epam.aidial.deployment.manager.web.dto.metrics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Latency distribution summary. Latency values ({@code mean}/{@code p50}/{@code p95}/{@code p99})
 * are in <b>seconds</b>; {@code count} is the number of observations. Percentiles are approximated
 * from cumulative Prometheus histogram buckets in a single snapshot (lifetime window).
 */
public record DistributionSummaryDto(
        @Schema(description = "Mean latency, in seconds", nullable = true)
        Double mean,
        @Schema(description = "50th percentile (median) latency, in seconds", nullable = true)
        Double p50,
        @Schema(description = "95th percentile latency, in seconds", nullable = true)
        Double p95,
        @Schema(description = "99th percentile latency, in seconds", nullable = true)
        Double p99,
        @Schema(description = "Number of observations in the distribution")
        long count) {
}
