package com.epam.aidial.deployment.manager.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when MCP client operation fails.
 */
public class McpClientException extends RuntimeException {

    @Getter
    private final HttpStatus httpStatus;

    /**
     * Creates a new McpClientException with the specified message and cause.
     *
     * @param message the error message
     * @param cause   the cause of the exception
     */
    public McpClientException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}
