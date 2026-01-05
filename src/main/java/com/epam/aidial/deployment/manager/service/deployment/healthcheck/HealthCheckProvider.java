package com.epam.aidial.deployment.manager.service.deployment.healthcheck;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@LogExecution
@RequiredArgsConstructor
public class HealthCheckProvider {

    private final List<HealthChecker> healthCheckers;
    private final DeploymentRepository deploymentRepository;

    public void waitReady(UUID deploymentId, String serviceUrl, Duration remainingDuration) {
        var deployment = deploymentRepository.getById(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found: %s".formatted(deploymentId)));
        healthCheckers.stream()
                .filter(checker -> checker.supports(deployment))
                .findFirst()
                .ifPresent(checker -> checker.waitReady(serviceUrl, deployment, remainingDuration));
    }
}

