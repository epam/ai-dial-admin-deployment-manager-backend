package com.epam.aidial.deployment.manager.web.dto.nodepool;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.Toleration;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Schema(description = "Node pool configuration")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NodePoolDto(
        @Schema(description = "Pool name", example = "gpu_node_pool")
        String name,
        @Nullable
        @Schema(description = "Human-readable description")
        String description,
        @Nullable
        @Schema(description = "Optional node selector (key=value map)")
        Map<String, String> nodeSelector,
        @Nullable
        @Schema(description = "Optional Kubernetes Affinity object")
        Affinity affinity,
        @Nullable
        @Schema(description = "Optional list of Kubernetes Tolerations")
        List<Toleration> tolerations
) {
}
