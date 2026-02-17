package com.epam.aidial.deployment.manager.mcpregistry.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.mcpregistry.client.McpRegistryClientException;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerListResponse;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerResponse;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServersRequest;
import com.epam.aidial.deployment.manager.mcpregistry.service.McpRegistryService;
import com.epam.aidial.deployment.manager.mcpregistry.web.dto.ServerVersionsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@LogExecution
@RestController
@RequestMapping("/api/v1/mcp-registry/servers")
@RequiredArgsConstructor
public class McpRegistryController {

    private final McpRegistryService mcpRegistryService;

    @GetMapping(produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServerListResponse> getServers(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) @Min(1) @Max(1000) Integer limit,
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
        try {
            return ResponseEntity.ok(mcpRegistryService.getServers(request));
        } catch (McpRegistryClientException e) {
            log.warn("Upstream error: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    /**
     * List all versions of a server. Server name is passed as namespace/name so that a slash in the
     * name does not cause 400 (encoded slash is often rejected by servlet containers).
     */
    @GetMapping(value = "/{namespace}/{name}/versions", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServerListResponse> getServerVersions(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        var serverName = namespace + "/" + name;
        try {
            return ResponseEntity.ok(mcpRegistryService.getServerVersions(serverName));
        } catch (McpRegistryClientException e) {
            log.warn("Upstream error: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    /**
     * Get a specific server version. Server name is passed as namespace/name so that a slash in the
     * name does not cause 400 (encoded slash is often rejected by servlet containers).
     */
    @GetMapping(value = "/{namespace}/{name}/versions/{version}", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServerResponse> getServerVersion(
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable String version
    ) {
        var serverName = namespace + "/" + name;
        try {
            return ResponseEntity.ok(mcpRegistryService.getServerVersion(serverName, version));
        } catch (McpRegistryClientException e) {
            log.warn("Upstream error: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    /**
     * List servers with all parameters in the request body.
     */
    @PostMapping(value = "/list", produces = MimeTypeUtils.APPLICATION_JSON_VALUE, consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServerListResponse> postListServers(@RequestBody @Valid ServersRequest request) {
        try {
            return ResponseEntity.ok(mcpRegistryService.getServers(request));
        } catch (McpRegistryClientException e) {
            log.warn("Upstream error: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    /**
     * List all versions of a server or get a specific version. If {@code version} is present and non-blank,
     * returns that version; otherwise returns the list of all versions. Server name is in the request body.
     */
    @PostMapping(value = "/versions", produces = MimeTypeUtils.APPLICATION_JSON_VALUE, consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> postServerVersions(@RequestBody @Valid ServerVersionsRequest request) {
        var serverName = request.getServerName();
        try {
            if (request.getVersion() != null && !request.getVersion().isBlank()) {
                return ResponseEntity.ok(mcpRegistryService.getServerVersion(serverName, request.getVersion()));
            }
            return ResponseEntity.ok(mcpRegistryService.getServerVersions(serverName));
        } catch (McpRegistryClientException e) {
            log.warn("Upstream error: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }
}
