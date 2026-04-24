package com.epam.aidial.deployment.manager.web.dto.nodepool;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GPU specification per node")
public record GpuSpecDto(
        @Schema(description = "GPU model name", example = "NVIDIA A100")
        String name,
        @Schema(description = "VRAM capacity per GPU in bytes", example = "85899345920")
        long vramBytes,
        @Schema(description = "Number of GPUs per node", example = "4")
        int count
) {
}
