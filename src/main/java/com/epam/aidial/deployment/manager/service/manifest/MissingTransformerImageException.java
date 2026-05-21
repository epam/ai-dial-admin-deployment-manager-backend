package com.epam.aidial.deployment.manager.service.manifest;

/**
 * Raised when a text-classification inference deployment is about to be chained but the
 * transformer container's image is not configured. Maps to HTTP 500 — this is an operator
 * configuration error that must be fixed before any further deploy can succeed.
 */
public class MissingTransformerImageException extends RuntimeException {

    public MissingTransformerImageException(String message) {
        super(message);
    }
}
