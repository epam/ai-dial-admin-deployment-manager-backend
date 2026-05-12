package com.epam.aidial.deployment.manager.web.dto.nodepool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Schema(description = "Listing of configured node pools and the currently-configured fallback defaults")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NodePoolListResponseDto(
        @Schema(description = "Configured pool catalogue")
        List<NodePoolDto> pools,
        @Nullable
        @Schema(description = "Currently-configured fallback defaults")
        DefaultsDto defaults
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record DefaultsDto(
            @Nullable
            @Schema(description = "Pool id of the catch-all default pool (NODE_POOL_DEFAULT)")
            String defaultId,
            @Nullable
            @Schema(description = "Pool id of the model-workload override default pool (NODE_POOL_DEFAULT_MODEL)")
            String modelId
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return defaultId == null && modelId == null;
        }
    }
}
