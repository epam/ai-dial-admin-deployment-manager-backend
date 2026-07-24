package com.epam.aidial.deployment.manager.web.dto.metrics;

import java.util.List;

/** Resource block of the unified metrics schema. */
public record ResourceMetricsDto(ReplicasDto replicas, List<PodResourceUsageDto> pods) {

    public record ReplicasDto(int total, int ready) {
    }

    /**
     * GPU fields come from the DCGM exporter and are {@code null} for pods with no GPU telemetry.
     * {@code gpuMemoryBytes}/{@code gpuMemoryTotalBytes} are bytes; {@code gpuUtilization} is a ratio 0–1.
     */
    public record PodResourceUsageDto(
            String name,
            Double cpuMillicores,
            Double memoryBytes,
            Double gpuUtilization,
            Double gpuMemoryBytes,
            Double gpuMemoryTotalBytes
    ) {
    }
}
