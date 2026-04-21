package com.epam.aidial.deployment.manager.web.dto.nodepool;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Per-node resource specification from configuration")
public record NodeSpecDto(
        @Schema(description = "CPU capacity per node in millicores", example = "96000")
        long cpuMillis,
        @Schema(description = "Memory capacity per node in bytes", example = "687194767360")
        long memoryBytes,
        @Schema(description = "GPU count per node", example = "3")
        int gpu
) {
}
