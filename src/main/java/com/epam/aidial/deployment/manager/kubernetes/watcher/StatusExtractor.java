package com.epam.aidial.deployment.manager.kubernetes.watcher;

import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;

/**
 * Functional interface for extracting deployment status from Kubernetes resources.
 *
 * @param <T> The type of Kubernetes resource
 */
@FunctionalInterface
public interface StatusExtractor<T extends HasMetadata> {
    /**
     * Extracts the deployment status from a Kubernetes resource.
     *
     * @param resource The Kubernetes resource
     * @return The deployment status, or null if status cannot be determined
     */
    DeploymentStatus extract(T resource);
}
