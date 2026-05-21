package com.epam.aidial.deployment.manager.service.detection;

/**
 * Raised when the HuggingFace Hub call fails with a 5xx, times out, or otherwise produces
 * a transient transport failure. The deploy was not started and may be safely retried.
 * Maps to HTTP 502.
 */
public class HuggingFaceUpstreamException extends InferenceTaskDetectionException {

    public HuggingFaceUpstreamException(String modelName, String message, Throwable cause) {
        super(modelName, message, cause);
    }
}
