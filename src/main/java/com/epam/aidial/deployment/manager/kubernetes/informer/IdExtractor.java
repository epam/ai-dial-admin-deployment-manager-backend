package com.epam.aidial.deployment.manager.kubernetes.informer;

/**
 * Functional interface for extracting deployment ID from resource names.
 */
@FunctionalInterface
public interface IdExtractor {
    /**
     * Extracts a deployment ID from a Kubernetes resource name.
     *
     * @param resourceName The name of the Kubernetes resource
     * @return The extracted String, or null if extraction fails
     */
    String extract(String resourceName);
}
