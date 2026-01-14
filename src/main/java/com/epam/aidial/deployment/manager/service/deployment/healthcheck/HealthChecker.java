package com.epam.aidial.deployment.manager.service.deployment.healthcheck;

import com.epam.aidial.deployment.manager.model.deployment.Deployment;

import java.time.Duration;

public interface HealthChecker {

    boolean supports(Deployment deployment);

    void waitReady(String serviceUrl, Deployment deployment, Duration remainingDuration);
}
