package com.epam.aidial.deployment.manager.service.deployment.metrics;

/**
 * Input to an {@link EngineMetricsNormalizer}: the {@code predictor} index is the engine-bearing
 * pod's parsed metrics (always present); {@code transformer} is the optional pre/post-processing
 * pod of a chained KServe InferenceService, {@code null} for single-pod deployments. Generative
 * engines (vLLM/TGI/SGLang) read only {@link #predictor()}; the KServe Python ModelServer normalizer
 * combines both pods for an end-to-end latency.
 */
record EngineScrapeContext(MetricSampleIndex predictor, MetricSampleIndex transformer) {

    static EngineScrapeContext of(MetricSampleIndex predictor) {
        return new EngineScrapeContext(predictor, null);
    }

    boolean hasTransformer() {
        return transformer != null;
    }
}
