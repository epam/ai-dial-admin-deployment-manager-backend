package com.epam.aidial.deployment.manager.exception;

import com.epam.aidial.deployment.manager.model.ImageStatus;

import java.util.UUID;

public class ImageBuildNotInProgressException extends RuntimeException {

    private static final String MESSAGE_FORMAT = "Image build for '%s' is not in progress (current status: %s)";

    public ImageBuildNotInProgressException(UUID imageDefinitionId, ImageStatus currentStatus) {
        super(MESSAGE_FORMAT.formatted(imageDefinitionId, currentStatus != null ? currentStatus : ImageStatus.NOT_BUILT));
    }
}
