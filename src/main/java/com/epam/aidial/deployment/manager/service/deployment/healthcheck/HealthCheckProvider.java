package com.epam.aidial.deployment.manager.service.deployment.healthcheck;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class HealthCheckProvider {

    private final List<HealthChecker> healthCheckers;
    private final DeploymentRepository deploymentRepository;

    @Value("${app.deployment-healthcheck-enabled}")
    private boolean healthcheckEnabled;

    public void waitReady(String deploymentId, String serviceUrl, Duration remainingDuration) {
        if (!healthcheckEnabled) {
            log.debug("Not performing healthcheck for deployment '{}'. Feature is disabled.", deploymentId);
            return;
        }
        log.debug("Starting healthcheck for deployment '{}'.", deploymentId);
        var deployment = deploymentRepository.getById(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found: %s".formatted(deploymentId)));
        healthCheckers.stream()
                .filter(checker -> checker.supports(deployment))
                .findFirst()
                .ifPresent(checker -> checker.waitReady(serviceUrl, deployment, remainingDuration));
    }
}

