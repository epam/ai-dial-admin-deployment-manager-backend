package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateAdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@LogExecution
public class DeploymentManagerProvider {

    private final Map<Class<? extends Deployment>, DeploymentManager<?>> deploymentManagers;
    private final DeploymentRepository deploymentRepository;

    public DeploymentManagerProvider(List<DeploymentManager<?>> deploymentManagers, DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;

        this.deploymentManagers = deploymentManagers.stream()
                .flatMap(deploymentManager -> deploymentManager
                        .getSupportedDeploymentClasses().stream()
                        .map(deploymentClass -> Map.entry(deploymentClass, deploymentManager)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    }

    public DeploymentManager<?> provide(CreateDeployment request) {
        var deploymentClass = switch (request) {
            case CreateMcpDeployment ignored -> McpDeployment.class;
            case CreateInterceptorDeployment ignored -> InterceptorDeployment.class;
            case CreateAdapterDeployment ignored -> AdapterDeployment.class;
            case CreateNimDeployment ignored -> NimDeployment.class;
            case CreateInferenceDeployment ignored -> InferenceDeployment.class;
            default -> throw new IllegalArgumentException("Deployment type is not supported: %s. Deployment displayName: %s"
                    .formatted(request.getClass(), request.getDisplayName()));
        };
        return provide(deploymentClass, null, request.getDisplayName());
    }

    public DeploymentManager<?> provide(String deploymentId) {
        var deployment = deploymentRepository.getById(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found: %s".formatted(deploymentId)));
        return provide(deployment.getClass(), deployment.getId(), deployment.getDisplayName());
    }

    private DeploymentManager<?> provide(Class<? extends Deployment> deploymentClass, String id, String name) {
        var deploymentManager = deploymentManagers.get(deploymentClass);
        if (deploymentManager == null) {
            throw new IllegalArgumentException("Deployment type is not supported: %s. Deployment ID: %s. Deployment displayName: %s"
                    .formatted(deploymentClass, id, name));
        }
        return deploymentManager;
    }

}
