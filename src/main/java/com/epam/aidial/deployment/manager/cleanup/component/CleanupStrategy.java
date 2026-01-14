package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.model.ComponentType;

import java.util.UUID;

/**
 * Strategy interface for cleaning up different types of components.
 */
public interface CleanupStrategy {

    default void prepareForDeletion(UUID id) {
        // Default implementation does nothing
    }

    void delete(UUID id);

    ComponentType getComponentType();
}
