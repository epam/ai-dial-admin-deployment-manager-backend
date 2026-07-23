package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Per-pod GPU aggregate joined from the DCGM exporter before it is merged into
 * {@link PodResourceUsage}. Memory figures are bytes; {@code utilization} is a ratio 0–1. For a pod
 * bound to several GPUs, memory is summed across its GPUs and utilization is averaged.
 */
public record GpuPodUsage(
        String podName,
        Double utilization,
        Double memoryUsedBytes,
        Double memoryTotalBytes
) {
}
