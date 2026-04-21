package com.epam.aidial.deployment.manager.exception;

import com.epam.aidial.deployment.manager.model.ImageStatus;

public class ImageBuildNotInProgressException extends RuntimeException {

    private static final String MESSAGE_FORMAT = "Image build is not in progress (current status: %s)";

    public ImageBuildNotInProgressException(ImageStatus currentStatus) {
        super(MESSAGE_FORMAT.formatted(currentStatus != null ? currentStatus : ImageStatus.NOT_BUILT));
    }
}
