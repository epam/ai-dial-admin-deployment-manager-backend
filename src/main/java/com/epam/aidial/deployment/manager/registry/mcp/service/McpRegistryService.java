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

    public ServerListResponseDto getServers(ServersRequestDto request) {
        return mcpRegistryClient.getServers(request);
    }

    public ServerListResponseDto getServerVersions(String serverName) {
        return mcpRegistryClient.getServerVersions(serverName);
    }

    public ServerResponseDto getServerVersion(String serverName, String version) {
        return mcpRegistryClient.getServerVersion(serverName, version);
    }
}
