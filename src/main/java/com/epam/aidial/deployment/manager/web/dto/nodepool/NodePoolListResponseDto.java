package com.epam.aidial.deployment.manager.web.dto.nodepool;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Listing of configured node pools")
public record NodePoolListResponseDto(
        @Schema(description = "Configured pool catalogue")
        List<NodePoolDto> pools
) {
}
