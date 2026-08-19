package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Per-pod resource consumption. CPU/memory come from {@code metrics.k8s.io} (metrics-server); the
 * GPU fields come from the DCGM exporter and stay {@code null} for pods with no GPU telemetry (a
 * non-GPU deployment, an absent exporter, or a pod the exporter does not yet report). {@code gpu*}
 * memory figures are bytes; {@code gpuUtilization} is a ratio 0–1.
 */
public record PodResourceUsage(
        String name,
        Double cpuMillicores,
        Double memoryBytes,
        Double gpuUtilization,
        Double gpuMemoryBytes,
        Double gpuMemoryTotalBytes
) {
}
