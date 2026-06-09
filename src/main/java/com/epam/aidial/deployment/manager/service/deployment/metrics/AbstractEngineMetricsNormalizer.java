package com.epam.aidial.deployment.manager.service.deployment.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared scaffolding for the per-engine normalizers: the lifetime error-ratio guard and the
 * {@code rawCounters} assembly are identical across engines, only the metric names differ. Each
 * concrete normalizer keeps its own vocabulary constants, field mapping, and error-ratio
 * derivation (these legitimately diverge per engine).
 */
abstract class AbstractEngineMetricsNormalizer implements EngineMetricsNormalizer {

    /**
     * Guarded, clamped lifetime ratio: {@code null} when the denominator is absent or non-positive,
     * otherwise {@code numerator/denominator} clamped to {@code [0, 1]}.
     */
    protected static Double clampedRatio(double numerator, double denominator) {
        if (denominator <= 0) {
            return null;
        }
        return MetricSamples.clampRatio(numerator / denominator);
    }

    /**
     * Builds the {@code rawCounters} block from an ordered (unified name → source metric name)
     * mapping, echoing only the counters the engine actually exposed.
     */
    protected static Map<String, Double> rawCounters(MetricSampleIndex index, Map<String, String> unifiedToSource) {
        var rawCounters = new LinkedHashMap<String, Double>();
        unifiedToSource.forEach((unified, source) ->
                MetricSamples.sum(index, source).ifPresent(value -> rawCounters.put(unified, value)));
        return rawCounters;
    }

    /** Ordered (unified name → source metric name) pairs for {@link #rawCounters}. */
    protected static Map<String, String> rawCounterSources(String... unifiedThenSource) {
        var mapping = new LinkedHashMap<String, String>();
        for (int i = 0; i < unifiedThenSource.length; i += 2) {
            mapping.put(unifiedThenSource[i], unifiedThenSource[i + 1]);
        }
        return mapping;
    }
}
