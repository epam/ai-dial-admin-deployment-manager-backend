package com.epam.aidial.deployment.manager.exception;

public class ImageBuildStopFailedException extends RuntimeException {

    private static final String MESSAGE_FORMAT =
            "Image build could not be stopped: %s; build remains in BUILDING and may be retried";

    public ImageBuildStopFailedException(Throwable cause) {
        super(MESSAGE_FORMAT.formatted(cause != null ? cause.getMessage() : "unknown cause"), cause);
    }
}
