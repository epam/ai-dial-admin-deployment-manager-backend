package com.epam.aidial.deployment.manager.mcpregistry.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.mcpregistry.client.McpRegistryClientException;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerListMetadata;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerListResponse;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerResponse;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServersRequest;
import com.epam.aidial.deployment.manager.mcpregistry.service.McpRegistryService;
import com.epam.aidial.deployment.manager.mcpregistry.web.dto.ServerVersionsRequest;
import com.epam.aidial.deployment.manager.web.handler.ErrorView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Slf4j
@LogExecution
@RestController
@RequestMapping("/api/v1/mcp-registry/servers")
@RequiredArgsConstructor
public class McpRegistryController {

    private final McpRegistryService mcpRegistryService;

    @GetMapping(produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerListResponse getServers(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(1000) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String updatedSince,
            @RequestParam(required = false) String version
    ) {
        var request = ServersRequest.builder()
                .cursor(cursor)
                .limit(limit)
                .search(search)
                .updatedSince(updatedSince)
                .version(version)
                .build();
        return mcpRegistryService.getServers(request);
    }

    /**
     * List all versions of a server. Server name is passed as namespace/name so that a slash in the
     * name does not cause 400 (encoded slash is often rejected by servlet containers).
     */
    @GetMapping(value = "/{namespace}/{name}/versions", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerListResponse getServerVersions(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        var serverName = namespace + "/" + name;
        return mcpRegistryService.getServerVersions(serverName);
    }

    /**
     * Get a specific server version. Server name is passed as namespace/name so that a slash in the
     * name does not cause 400 (encoded slash is often rejected by servlet containers).
     */
    @GetMapping(value = "/{namespace}/{name}/versions/{version}", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerResponse getServerVersion(
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable String version
    ) {
        var serverName = namespace + "/" + name;
        return mcpRegistryService.getServerVersion(serverName, version);
    }

    /**
     * List servers with all parameters in the request body.
     */
    @PostMapping(value = "/list", produces = MimeTypeUtils.APPLICATION_JSON_VALUE, consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerListResponse postListServers(@RequestBody @Valid ServersRequest request) {
        return mcpRegistryService.getServers(request);
    }

    /**
     * List all versions of a server or get a specific version. If {@code version} is present and non-blank,
     * returns a {@link ServerListResponse} containing that single version; otherwise returns the list of all
     * versions. Server name is in the request body.
     */
    @PostMapping(value = "/versions", produces = MimeTypeUtils.APPLICATION_JSON_VALUE, consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ServerListResponse postServerVersions(@RequestBody @Valid ServerVersionsRequest request) {
        var serverName = request.getServerName();
        if (request.getVersion() != null && !request.getVersion().isBlank()) {
            var one = mcpRegistryService.getServerVersion(serverName, request.getVersion());
            return ServerListResponse.builder()
                .servers(List.of(one))
                .metadata(ServerListMetadata.builder().count(1).build())
                .build();
        }
        return mcpRegistryService.getServerVersions(serverName);
    }

    @ExceptionHandler(McpRegistryClientException.class)
    public ResponseEntity<ErrorView> handleDeploymentError(HttpServletRequest req, McpRegistryClientException ex) {
        log.debug("Upstream error: ", ex);
        var status = Optional.ofNullable(HttpStatus.resolve(ex.getStatusCode())).orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorView(req, status, ex.getMessage()));
    }

}
