package com.epam.aidial.deployment.manager.mcpregistry.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerVersionsRequest {

    /**
     * Full server name (e.g. ai.com.mcp/petstore).
     */
    @NotBlank
    private String serverName;

    /**
     * Optional version (e.g. 1.0.0 or latest). If present, returns that version; otherwise lists all versions.
     */
    private String version;
}
