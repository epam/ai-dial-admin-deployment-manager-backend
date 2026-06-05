package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Per-pod resource consumption. GPU fields are present in the contract but always {@code null}
 * in the PoC — they require the DCGM exporter cluster prerequisite (follow-up).
 */
public record PodResourceUsage(
        String name,
        Double cpuMillicores,
        Double memoryBytes,
        Double gpuUtilization,
        Double gpuMemoryUsedBytes
) {
}
