package com.epam.aidial.deployment.manager.exception;

/**
 * Exception thrown when an attempt is made to delete an image that is currently in use by deployments.
 */
public class ImageInUseException extends RuntimeException {

    public ImageInUseException(String message) {
        super(message);
    }
}