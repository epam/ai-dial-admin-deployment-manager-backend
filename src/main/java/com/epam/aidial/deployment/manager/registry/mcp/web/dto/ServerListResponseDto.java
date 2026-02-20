package com.epam.aidial.deployment.manager.registry.mcp.web.dto;

import com.epam.aidial.deployment.manager.registry.mcp.model.ServerListMetadata;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerListResponseDto {

    /**
     * List of MCP server entries (each with server detail and optional _meta).
     */
    private List<ServerResponseDto> servers;

    /**
     * Pagination and count metadata.
     */
    @Nullable
    private ServerListMetadata metadata;
}
