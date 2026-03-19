package com.epam.aidial.deployment.manager.model.config;

public record ImportValidationError(
        String entityType,
        String entityIdentifier,
        String fieldPath,
        String message
) {
}
