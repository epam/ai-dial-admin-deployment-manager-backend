package com.epam.aidial.deployment.manager.exception;

public class ImageBuildStopFailedException extends RuntimeException {

    private static final String MESSAGE = "Image build could not be stopped; build remains in BUILDING and may be retried";

    public ImageBuildStopFailedException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
