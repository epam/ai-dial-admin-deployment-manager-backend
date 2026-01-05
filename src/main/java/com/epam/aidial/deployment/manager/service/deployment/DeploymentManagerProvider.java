package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@LogExecution
@RequiredArgsConstructor
public class DeploymentManagerProvider {

    private final KnativeDeploymentManager knativeDeploymentManager;
    private final NimDeploymentManager nimDeploymentManager;
    private final InferenceDeploymentManager inferenceDeploymentManager;

    private final DeploymentRepository deploymentRepository;

    public DeploymentManager<?> provide(CreateDeployment request) {
        if (request instanceof CreateInferenceDeployment) {
            return inferenceDeploymentManager;
        }
        if (request instanceof CreateNimDeployment) {
            return nimDeploymentManager;
        }
        if (request instanceof CreateMcpDeployment || request instanceof CreateInterceptorDeployment) {
            return knativeDeploymentManager;
        }
        throw new IllegalArgumentException("Deployment type is not supported: %s. Deployment name: %s"
                .formatted(request.getClass(), request.getName()));
    }

    public DeploymentManager<?> provide(UUID deploymentId) {
        var deployment = deploymentRepository.getById(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found: %s".formatted(deploymentId)));

        if (deployment instanceof InferenceDeployment) {
            return inferenceDeploymentManager;
        }
        if (deployment instanceof NimDeployment) {
            return nimDeploymentManager;
        }
        if (deployment instanceof McpDeployment || deployment instanceof InterceptorDeployment) {
            return knativeDeploymentManager;
        }

        throw new IllegalArgumentException("Deployment type is not supported: %s. Deployment id: %s. Deployment name: %s"
                .formatted(deployment.getClass(), deploymentId, deployment.getName()));
    }

}
