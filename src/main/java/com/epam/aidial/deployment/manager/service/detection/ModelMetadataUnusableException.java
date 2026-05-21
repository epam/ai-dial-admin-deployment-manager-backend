package com.epam.aidial.deployment.manager.service.detection;

/**
 * Raised when a HuggingFace model's {@code id2label} fails the structural contract:
 * non-integer keys, sparse keys, empty values, or all values matching HF's auto-stub
 * pattern {@code ^LABEL_\d+$}. Maps to HTTP 400.
 */
public class ModelMetadataUnusableException extends InferenceTaskDetectionException {

    public ModelMetadataUnusableException(String modelName, String message) {
        super(modelName, message);
    }

    public ModelMetadataUnusableException(String modelName, String message, Throwable cause) {
        super(modelName, message, cause);
    }
}
