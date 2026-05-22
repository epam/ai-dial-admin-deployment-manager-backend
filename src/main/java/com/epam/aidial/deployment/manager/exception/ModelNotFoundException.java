package com.epam.aidial.deployment.manager.exception;

/**
 * Raised when the HuggingFace Hub responds 404 (model not found) or 401/403 (private model
 * without credentials) for the requested model. Maps to HTTP 400.
 */
public class ModelNotFoundException extends InferenceTaskDetectionException {

    public ModelNotFoundException(String modelName, String message, Throwable cause) {
        super(modelName, message, cause);
    }
}
