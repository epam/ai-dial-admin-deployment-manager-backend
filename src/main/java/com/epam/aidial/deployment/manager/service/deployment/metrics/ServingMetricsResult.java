package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.AvailabilityStatus;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;

/**
 * Outcome of a {@link ServingMetricsCollector#collect} call: the detected engine, the scraped pod
 * (when one was reached), the normalized metrics ({@code null} when degraded), and the availability
 * status shared by the {@code serving} and {@code operational} blocks.
 */
public record ServingMetricsResult(EngineFamily engine, String scrapedPod,
                                   NormalizedEngineMetrics normalized, AvailabilityStatus availability) {

    /** Degraded result: no normalized metrics, the {@code reason} recorded for serving/operational. */
    public static ServingMetricsResult unavailable(EngineFamily engine, String scrapedPod, String reason) {
        return new ServingMetricsResult(engine, scrapedPod, null, AvailabilityStatus.unavailable(reason));
    }

    /** Successful result: serving/operational blocks available. */
    public static ServingMetricsResult available(EngineFamily engine, String scrapedPod, NormalizedEngineMetrics normalized) {
        return new ServingMetricsResult(engine, scrapedPod, normalized, AvailabilityStatus.AVAILABLE);
    }
}
