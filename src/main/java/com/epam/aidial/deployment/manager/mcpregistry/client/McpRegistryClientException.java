package com.epam.aidial.deployment.manager.mcpregistry.client;

import lombok.Getter;

/**
 * Exception thrown when an MCP Registry API request fails with a specific HTTP status code.
 */
@Getter
public class McpRegistryClientException extends RuntimeException {

    private final int statusCode;

    public McpRegistryClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

}
