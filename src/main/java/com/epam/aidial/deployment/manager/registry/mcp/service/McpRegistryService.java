package com.epam.aidial.deployment.manager.registry.mcp.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClient;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerListResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServersRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class McpRegistryService {

    private final McpRegistryClient mcpRegistryClient;

    /**
     * Get a page of MCP servers from the registry.
     *
     * @param request request parameters (cursor, limit, search, updatedSince, version)
     * @return paginated response with servers and metadata
     */
    public ServerListResponseDto getServers(ServersRequestDto request) {
        return mcpRegistryClient.getServers(request);
    }

    /**
     * Get all versions of a specific MCP server.
     *
     * @param serverName server name (e.g. ai.com.mcp/petstore); encoded when calling upstream
     * @return list of server entries (one per version)
     */
    public ServerListResponseDto getServerVersions(String serverName) {
        return mcpRegistryClient.getServerVersions(serverName);
    }

    /**
     * Get a specific version of an MCP server. Use "latest" for the latest version.
     *
     * @param serverName server name (e.g. ai.com.mcp/petstore)
     * @param version    version string (e.g. 1.0.0 or latest)
     * @return server detail and optional registry metadata
     */
    public ServerResponseDto getServerVersion(String serverName, String version) {
        return mcpRegistryClient.getServerVersion(serverName, version);
    }
}
