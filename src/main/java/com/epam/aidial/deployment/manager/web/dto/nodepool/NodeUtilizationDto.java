package com.epam.aidial.deployment.manager.web.dto.nodepool;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resource utilization snapshot for a single running node")
public record NodeUtilizationDto(
        @Schema(description = "Kubernetes node name", example = "gke-gpu-a100-pool-abc123")
        String nodeName,
        @Schema(description = "Allocatable CPU in millicores", example = "95500")
        long allocatableCpuMillis,
        @Schema(description = "Allocatable memory in bytes", example = "684500000000")
        long allocatableMemoryBytes,
        @Schema(description = "Allocatable GPU count", example = "3")
        int allocatableGpu,
        @Schema(description = "Sum of pod CPU requests in millicores", example = "57300")
        long requestedCpuMillis,
        @Schema(description = "Sum of pod memory requests in bytes", example = "410700000000")
        long requestedMemoryBytes,
        @Schema(description = "Sum of pod GPU requests", example = "2")
        int requestedGpu
) {
}
