package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Recognized model-serving engine vocabularies that determine how raw engine metrics
 * map to the unified schema. {@code metricNamePrefix} is the distinctive exposition prefix used
 * to sniff inference engines from their exposed series; {@code UNKNOWN} is identified by exclusion
 * and so carries no prefix.
 */
public enum EngineFamily {
    VLLM("vllm:"),
    TGI("tgi_"),
    SGLANG("sglang:"),
    UNKNOWN(null);

    private final String metricNamePrefix;

    EngineFamily(String metricNamePrefix) {
        this.metricNamePrefix = metricNamePrefix;
    }

    /** Distinctive exposition metric-name prefix, or {@code null} when the family isn't prefix-detectable. */
    public String metricNamePrefix() {
        return metricNamePrefix;
    }
}
