package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@LogExecution
@RequiredArgsConstructor
public class McpEndpointPathResolver {

    public String resolveEndpointPath(McpDeployment deployment) {
        // 1. If deployment.mcpEndpointPath is set, return it (override)
        if (StringUtils.isNotBlank(deployment.getMcpEndpointPath())) {
            return deployment.getMcpEndpointPath();
        }

        // 2. Else return transport-specific default based on deployment.getTransport()
        return getDefaultPathForTransport(deployment.getTransport());
    }

    private String getDefaultPathForTransport(McpTransport transport) {
        return switch (transport) {
            case HTTP_STREAMING -> "/mcp";
            case SSE -> "/sse";
        };
    }
}
