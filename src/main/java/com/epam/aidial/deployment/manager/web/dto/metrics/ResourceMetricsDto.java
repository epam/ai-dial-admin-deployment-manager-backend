package com.epam.aidial.deployment.manager.web.dto.metrics;

import java.util.List;

/** Resource block of the unified metrics schema. */
public record ResourceMetricsDto(ReplicasDto replicas, List<PodResourceUsageDto> pods) {

    public record ReplicasDto(int total, int ready) {
    }

    /** GPU fields require the DCGM exporter cluster prerequisite and are always {@code null} in the PoC. */
    public record PodResourceUsageDto(
            String name,
            Double cpuMillicores,
            Double memoryBytes,
            Double gpuUtilization,
            Double gpuMemoryBytes
    ) {
    }
}
