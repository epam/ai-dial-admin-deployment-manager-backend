package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.ParsedExposition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the serving engine family from a parsed exposition. Prefix-detectable engines
 * ({@code vllm:} / {@code tgi_} / {@code sglang:}) are sniffed from the value-bearing series they
 * always expose. The KServe Python ModelServer framework (classification/embedding/custom
 * predictors) exposes unprefixed {@code request_*_seconds} histograms that are label-gated and emit
 * no samples while idle, so it is detected from the metric names declared in {@code # TYPE} lines
 * instead. Falls back to {@code UNKNOWN} when nothing matches. Persisting the serving runtime at
 * deploy time is the recorded durable replacement (spike §7(c)).
 */
@Component
@LogExecution
public class EngineDetector {

    /** Prefix-detectable inference engines, sniffed from their exposed series. */
    private static final List<EngineFamily> PREFIX_DETECTABLE = List.of(
            EngineFamily.VLLM, EngineFamily.TGI, EngineFamily.SGLANG);

    /**
     * Distinctive KServe Python ModelServer histogram base name. Declared via {@code # TYPE} on
     * every ModelServer regardless of traffic, so detection works even before the first request.
     */
    private static final String KSERVE_MARKER_METRIC = "request_predict_seconds";

    public EngineFamily detect(ParsedExposition exposition) {
        for (var sample : exposition.samples()) {
            var name = sample.name();
            for (var family : PREFIX_DETECTABLE) {
                if (name.startsWith(family.metricNamePrefix())) {
                    return family;
                }
            }
        }
        if (exposition.declaredMetricNames().contains(KSERVE_MARKER_METRIC)) {
            return EngineFamily.KSERVE_MODELSERVER;
        }
        return EngineFamily.UNKNOWN;
    }

}
