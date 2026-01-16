package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.model.ComponentType;

/**
 * Strategy interface for cleaning up different types of components.
 */
public interface CleanupStrategy {

    default void prepareForDeletion(String id) {
        // Default implementation does nothing
    }

    void delete(String id);

    ComponentType getComponentType();
}
