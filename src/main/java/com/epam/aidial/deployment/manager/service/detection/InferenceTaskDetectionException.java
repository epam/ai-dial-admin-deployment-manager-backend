package com.epam.aidial.deployment.manager.service.detection;

import lombok.Getter;

/**
 * Thrown by {@link InferenceTaskDetector} when a HuggingFace model is classified as a chained
 * task target but its metadata is unusable. Mapped to HTTP 400 by the web exception handler.
 */
@Getter
public class InferenceTaskDetectionException extends RuntimeException {

    private final String modelName;

    public InferenceTaskDetectionException(String modelName, String message) {
        super(message);
        this.modelName = modelName;
    }
}
