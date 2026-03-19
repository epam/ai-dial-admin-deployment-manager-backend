package com.epam.aidial.deployment.manager.registry.mcp.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.registry.mcp.model.ServerListMetadata;
import com.epam.aidial.deployment.manager.registry.mcp.service.McpRegistryService;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerFilterDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerListResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerVersionsRequestDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServersRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@LogExecution
@RestController
@RequestMapping("/api/v1/mcp-registry/servers")
@RequiredArgsConstructor
public class McpRegistryController {

    private final McpRegistryService mcpRegistryService;

    @Operation(summary = "List MCP servers with optional backend filtering")
    @GetMapping(produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerListResponseDto getServers(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(1000) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String updatedSince,
            @RequestParam(required = false) String version,
            @Parameter(description = "Remote transport types to filter by (OR logic). Values: sse, streamable-http")
            @RequestParam(required = false) List<String> remoteTypes,
            @Parameter(description = "Package registry types to filter by (OR logic). Values: npm, pypi, oci, nuget, mcpb")
            @RequestParam(required = false) List<String> packageRegistryTypes,
            @Parameter(description = "Filter by repository existence. true = only servers with repository, false = only without")
            @RequestParam(required = false) Boolean repositoryExists
    ) {
        var filter = buildFilter(remoteTypes, packageRegistryTypes, repositoryExists);
        var request = ServersRequestDto.builder()
                .cursor(cursor)
                .limit(limit)
                .search(search)
                .updatedSince(updatedSince)
                .version(version)
                .filter(filter)
                .build();
        return mcpRegistryService.getServers(request);
    }

    @GetMapping(value = "/{namespace}/{name}/versions", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerListResponseDto getServerVersions(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        var serverName = namespace + "/" + name;
        return mcpRegistryService.getServerVersions(serverName);
    }

    @GetMapping(value = "/{namespace}/{name}/versions/{version}", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerResponseDto getServerVersion(
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable String version
    ) {
        var serverName = namespace + "/" + name;
        return mcpRegistryService.getServerVersion(serverName, version);
    }

    @PostMapping(value = "/list", produces = MimeTypeUtils.APPLICATION_JSON_VALUE, consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerListResponseDto postListServers(@RequestBody @Valid ServersRequestDto request) {
        return mcpRegistryService.getServers(request);
    }

    @PostMapping(value = "/versions", produces = MimeTypeUtils.APPLICATION_JSON_VALUE, consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerListResponseDto postServerVersions(@RequestBody @Valid ServerVersionsRequestDto request) {
        var serverName = request.getServerName();
        if (StringUtils.isNotBlank(request.getVersion())) {
            var one = mcpRegistryService.getServerVersion(serverName, request.getVersion());
            return ServerListResponseDto.builder()
                .servers(List.of(one))
                .metadata(ServerListMetadata.builder().count(1).build())
                .build();
        }
        return mcpRegistryService.getServerVersions(serverName);
    }

    private static ServerFilterDto buildFilter(List<String> remoteTypes,
                                               List<String> packageRegistryTypes,
                                               Boolean repositoryExists) {
        if (remoteTypes == null && packageRegistryTypes == null && repositoryExists == null) {
            return null;
        }
        return ServerFilterDto.builder()
                .remoteTypes(remoteTypes)
                .packageRegistryTypes(packageRegistryTypes)
                .repositoryExists(repositoryExists)
                .build();
    }
}
