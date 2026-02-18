package com.epam.aidial.deployment.manager.mcpregistry.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mcp-registry")
public class McpRegistryProperties {
    /**
     * Base URL of the MCP Registry API (e.g. https://registry.modelcontextprotocol.io).
     */
    private String baseUrl;
}
