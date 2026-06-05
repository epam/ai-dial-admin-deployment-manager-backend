package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;

import java.util.List;

/**
 * Maps one engine family's metric vocabulary to the unified schema. Implementations are
 * selected via {@link #supports(EngineFamily)} from the injected list (mirrors the
 * {@code HealthChecker.supports} pattern). Absent engine metrics yield {@code null} fields,
 * never errors.
 */
public interface EngineMetricsNormalizer {

    boolean supports(EngineFamily family);

    NormalizedEngineMetrics normalize(List<MetricSample> samples);
}
