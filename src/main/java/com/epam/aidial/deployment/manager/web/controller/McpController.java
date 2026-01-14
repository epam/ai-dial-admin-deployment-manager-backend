package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.service.McpService;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deployments/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;

    @GetMapping(path = "/{deploymentId}/tools",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public McpSchema.ListToolsResult getTools(@PathVariable UUID deploymentId,
                                              @RequestParam(required = false) String nextCursor) {
        return mcpService.getTools(deploymentId, nextCursor);
    }

    @GetMapping(path = "/{deploymentId}/resources",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public McpSchema.ListResourcesResult getResources(@PathVariable UUID deploymentId,
                                                      @RequestParam(required = false) String nextCursor) {
        return mcpService.getResources(deploymentId, nextCursor);
    }

    @GetMapping(path = "/{deploymentId}/prompts",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public McpSchema.ListPromptsResult getPrompts(@PathVariable UUID deploymentId,
                                                  @RequestParam(required = false) String nextCursor) {
        return mcpService.getPrompts(deploymentId, nextCursor);
    }

}
