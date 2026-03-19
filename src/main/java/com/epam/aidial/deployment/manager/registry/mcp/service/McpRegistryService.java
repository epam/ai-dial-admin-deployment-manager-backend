package com.epam.aidial.deployment.manager.registry.mcp.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClient;
import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClientException;
import com.epam.aidial.deployment.manager.registry.mcp.model.ServerListMetadata;
import com.epam.aidial.deployment.manager.registry.mcp.properties.McpRegistryProperties;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerFilterDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerListResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServersRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class McpRegistryService {

    private final McpRegistryClient mcpRegistryClient;
    private final McpServerFilter mcpServerFilter;
    private final McpRegistryProperties mcpRegistryProperties;

    public ServerListResponseDto getServers(ServersRequestDto request) {
        var filter = request.getFilter();
        if (!mcpServerFilter.hasActiveCriteria(filter)) {
            return mcpRegistryClient.getServers(request);
        }
        return getServersWithFiltering(request, filter);
    }

    public ServerListResponseDto getServerVersions(String serverName) {
        return mcpRegistryClient.getServerVersions(serverName);
    }

    public ServerResponseDto getServerVersion(String serverName, String version) {
        return mcpRegistryClient.getServerVersion(serverName, version);
    }

    private ServerListResponseDto getServersWithFiltering(ServersRequestDto request, ServerFilterDto filter) {
        int maxPages = mcpRegistryProperties.getMaxPagesToScan();
        int pageSize = request.getLimit() != null ? request.getLimit() : 100;
        var collected = new ArrayList<ServerResponseDto>();
        String currentCursor = request.getCursor();
        String lastSuccessfulCursor = currentCursor;
        int pagesScanned = 0;

        while (pagesScanned < maxPages && collected.size() < pageSize) {
            var upstreamRequest = ServersRequestDto.builder()
                    .cursor(currentCursor)
                    .limit(request.getLimit())
                    .search(request.getSearch())
                    .updatedSince(request.getUpdatedSince())
                    .version(request.getVersion())
                    .build();

            ServerListResponseDto upstreamResponse;
            try {
                upstreamResponse = mcpRegistryClient.getServers(upstreamRequest);
            } catch (McpRegistryClientException e) {
                if (CollectionUtils.isNotEmpty(collected)) {
                    log.warn("Error mid-scan at page {}. Returning {} partial results.", pagesScanned + 1, collected.size(), e);
                    return buildResponse(collected, lastSuccessfulCursor);
                }
                throw e;
            }
            pagesScanned++;

            var servers = upstreamResponse.getServers();
            if (CollectionUtils.isNotEmpty(servers)) {
                for (var server : servers) {
                    if (mcpServerFilter.matches(server, filter)) {
                        collected.add(server);
                    }
                }
            }

            var metadata = upstreamResponse.getMetadata();
            var nextCursor = metadata != null ? metadata.getNextCursor() : null;
            if (nextCursor == null) {
                return buildResponse(collected, null);
            }
            lastSuccessfulCursor = nextCursor;
            currentCursor = nextCursor;
        }

        return buildResponse(collected, lastSuccessfulCursor);
    }

    private static ServerListResponseDto buildResponse(List<ServerResponseDto> servers, String nextCursor) {
        return ServerListResponseDto.builder()
                .servers(servers)
                .metadata(ServerListMetadata.builder()
                        .nextCursor(nextCursor)
                        .count(servers.size())
                        .build())
                .build();
    }
}
