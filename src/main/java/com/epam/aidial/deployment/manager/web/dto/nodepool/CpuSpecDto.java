package com.epam.aidial.deployment.manager.web.dto.nodepool;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.Nullable;

@Schema(description = "CPU specification per node")
public record CpuSpecDto(
        @Nullable
        @Schema(description = "Processor name", example = "AMD EPYC Milan")
        String name,
        @Schema(description = "CPU capacity per node in millicores", example = "48000")
        long milliCpus
) {
}
