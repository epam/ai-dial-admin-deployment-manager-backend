package com.epam.aidial.deployment.manager.web.dto.nodepool;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Memory specification per node")
public record MemorySpecDto(
        @Schema(description = "Memory capacity per node in bytes", example = "730144440320")
        long bytes
) {
}
