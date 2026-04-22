package com.epam.aidial.deployment.manager.web.dto.nodepool;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.Nullable;

@Schema(description = "Node pool configuration")
public record NodePoolDto(
        @Schema(description = "Pool name", example = "gpu-a100-prod")
        String name,
        @Nullable
        @Schema(description = "Human-readable description", example = "LLM inference & fine-tuning")
        String description,
        @Nullable
        @Schema(description = "Cloud instance type", example = "a2-ultragpu-4g")
        String instance,
        @Schema(description = "Maximum number of nodes in this pool", example = "10")
        int maxNodes,
        @Nullable
        @Schema(description = "GPU specification per node, null for CPU-only pools")
        GpuSpecDto gpu,
        @Schema(description = "CPU specification per node")
        CpuSpecDto cpu,
        @Schema(description = "Memory specification per node")
        MemorySpecDto memory
) {
}
