package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Serving-quality block of the unified schema. Absent engine metrics yield {@code null} fields
 * while the block itself stays available. The leading fields are generative-engine signals
 * (vLLM/TGI/SGLang); {@code requestLatency} and {@code requestsPerSecond} are engine-neutral
 * single-request signals populated for non-generative engines such as the KServe Python
 * ModelServer (where token/KV-cache/queue fields do not apply and stay {@code null}).
 */
public record ServingMetrics(
        DistributionSummary ttft,
        DistributionSummary interTokenLatency,
        Double promptTokensPerSecond,
        Double generationTokensPerSecond,
        Integer queueDepth,
        Integer runningRequests,
        Double kvCacheUsage,
        DistributionSummary requestLatency,
        Double requestsPerSecond
) {
}
