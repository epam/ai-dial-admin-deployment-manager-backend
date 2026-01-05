package com.epam.aidial.deployment.manager.exception;

/**
 * Exception thrown when a deployment operation fails.
 */
public class DeploymentException extends RuntimeException {

    /**
     * Creates a new DeploymentException with the specified message.
     *
     * @param message the error message
     */
    public DeploymentException(String message) {
        super(message);
    }

    /**
     * Creates a new DeploymentException with the specified message and cause.
     *
     * @param message the error message
     * @param cause   the cause of the exception
     */
    public DeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
} 