package com.epam.aidial.deployment.manager.exception;

import lombok.Getter;

/**
 * Base class for failures raised by
 * {@code com.epam.aidial.deployment.manager.service.detection.InferenceTaskDetector} when a
 * HuggingFace model cannot be successfully classified or its metadata cannot produce a usable
 * {@code id2Label} map. Concrete subclasses convey the specific cause; the web handler maps
 * each subclass to the HTTP status documented in
 * {@code specs/021-inference-task-transformer/contracts/inference-deployment-api.md}.
 */
@Getter
public abstract class InferenceTaskDetectionException extends RuntimeException {

    private final String modelName;

    protected InferenceTaskDetectionException(String modelName, String message) {
        super(message);
        this.modelName = modelName;
    }

    protected InferenceTaskDetectionException(String modelName, String message, Throwable cause) {
        super(message, cause);
        this.modelName = modelName;
    }
}
