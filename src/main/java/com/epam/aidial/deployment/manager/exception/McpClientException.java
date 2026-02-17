package com.epam.aidial.deployment.manager.exception;

/**
 * Exception thrown when MCP client operation fails.
 */
public class McpClientException extends RuntimeException {

    /**
     * Creates a new McpClientException with the specified message and cause.
     *
     * @param message the error message
     * @param cause   the cause of the exception
     */
    public McpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
