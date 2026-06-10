package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;

/**
 * Maps one engine family's metric vocabulary to the unified schema. Implementations are
 * selected via {@link #supports(EngineFamily)} from the injected list (mirrors the
 * {@code HealthChecker.supports} pattern). Absent engine metrics yield {@code null} fields,
 * never errors. The {@link EngineScrapeContext} carries the engine-bearing predictor index and,
 * for chained deployments, an optional transformer index.
 */
public interface EngineMetricsNormalizer {

    boolean supports(EngineFamily family);

    NormalizedEngineMetrics normalize(EngineScrapeContext context);
}
