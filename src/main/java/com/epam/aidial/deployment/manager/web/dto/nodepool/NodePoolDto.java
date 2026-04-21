package com.epam.aidial.deployment.manager.web.dto.nodepool;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Schema(description = "Node pool with configured spec and live Kubernetes utilization data")
public record NodePoolDto(
        @Schema(description = "Pool name", example = "gpu-a100-pool")
        String name,
        @Nullable
        @Schema(description = "Human-readable description", example = "NVIDIA A100 80 GB SXM — large model training and inference")
        String description,
        @Schema(description = "Maximum number of nodes in this pool", example = "8")
        int maxNodes,
        @Schema(description = "Number of currently running nodes", example = "3")
        int runningNodes,
        @Schema(description = "Per-node resource specification from configuration")
        NodeSpecDto nodeSpec,
        @Schema(description = "Per-node utilization data for each running node")
        List<NodeUtilizationDto> nodes
) {
}
