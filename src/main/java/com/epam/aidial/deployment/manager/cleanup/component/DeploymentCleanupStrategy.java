package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@LogExecution
class DeploymentCleanupStrategy extends AbstractCleanupStrategy {

    private final DeploymentRepository deploymentRepository;

    public DeploymentCleanupStrategy(DisposableResourceManager resourceManager,
                             DisposableResourceCleaner resourceCleaner,
                             DeploymentRepository deploymentRepository) {
        super(resourceManager, resourceCleaner);
        this.deploymentRepository = deploymentRepository;
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.DEPLOYMENT;
    }

    @Override
    @Transactional
    public void prepareForDeletion(UUID id) {
        deploymentRepository.conditionalUpdate(id,
                deployment -> deployment.getStatus().isActive(),
                deployment -> deployment.setStatus(DeploymentStatus.STOPPING));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        cleanupResources(id);
        deploymentRepository.deleteById(id);
        log.info("Deployment '{}' deleted successfully", id);
    }
}
