package com.epam.aidial.deployment.manager.web.dto.nodepool;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.Nullable;

@Schema(description = "Node pool configuration")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NodePoolDto(
        @Schema(description = "Immutable pool identifier (referenced by deployments)", example = "gpu-a100",
                requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
        String id,
        @Schema(description = "Human-readable display label (may be changed without breaking deployments)",
                example = "GPU A100 Pool", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
        String name,
        @Nullable
        @Schema(description = "Human-readable description")
        String description
) {
}
