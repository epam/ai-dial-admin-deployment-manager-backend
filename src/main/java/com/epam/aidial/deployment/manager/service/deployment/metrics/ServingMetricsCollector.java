package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.deployment.Deployment;

/**
 * Collects serving-quality metrics for the deployment types it {@link #supports(Deployment)}.
 * {@code DeploymentMetricsService} always collects resource metrics (shared across all types), then
 * delegates serving metrics to the first matching collector — so adding a new inference family
 * (e.g. NIM) is a new collector implementation with no change to the orchestrator.
 */
public interface ServingMetricsCollector {

    boolean supports(Deployment deployment);

    ServingMetricsResult collect(EngineScrapeTarget target);
}
