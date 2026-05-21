package com.epam.aidial.deployment.manager.service.detection;

/**
 * Raised when a HuggingFace model is detected as a sequence-classification model but its
 * {@code config.json} is missing the {@code id2label} field entirely. Maps to HTTP 400.
 */
public class ModelMetadataMissingException extends InferenceTaskDetectionException {

    public ModelMetadataMissingException(String modelName, String message) {
        super(modelName, message);
    }
}
