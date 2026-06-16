package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManager;

import java.util.List;

/**
 * Inputs a {@link ServingMetricsCollector} needs to scrape a deployment's serving metrics: the
 * deployment, its manager (for namespace and the default container port), and its Ready pods.
 */
public record EngineScrapeTarget(Deployment deployment, DeploymentManager<?> manager, List<PodInfo> readyPods) {

    /** Engine scrape port: the deployment's container port, falling back to the manager default. */
    public int port() {
        return deployment.getContainerPort() != null ? deployment.getContainerPort() : manager.getDefaultContainerPort();
    }

    public String namespace() {
        return manager.getNamespace();
    }
}
