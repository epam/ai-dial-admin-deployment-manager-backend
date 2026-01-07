package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.dao.repository.ImageDefinitionRepository;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@LogExecution
class ImageDefinitionCleanupStrategy extends AbstractCleanupStrategy {

    private final ImageDefinitionRepository imageDefinitionRepository;
    private final DeploymentRepository deploymentRepository;
    private final ComponentCleanupService componentCleanupService;

    public ImageDefinitionCleanupStrategy(DisposableResourceManager resourceManager,
                                  DisposableResourceCleaner resourceCleaner,
                                  ImageDefinitionRepository imageDefinitionRepository,
                                  DeploymentRepository deploymentRepository,
                                  @Lazy ComponentCleanupService componentCleanupService) {
        super(resourceManager, resourceCleaner);
        this.imageDefinitionRepository = imageDefinitionRepository;
        this.deploymentRepository = deploymentRepository;
        this.componentCleanupService = componentCleanupService;
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.IMAGE_DEFINITION;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        imageDefinitionRepository.getImageDefinitionForUpdateById(id);

        cleanupResources(id);

        // Clean up deployments that reference this image definition
        deploymentRepository.getAllByImageDefinitionId(id).stream()
                .map(Deployment::getId)
                .forEach(deploymentId
                        -> componentCleanupService.deleteAsync(ComponentRemoval.of(deploymentId, ComponentType.DEPLOYMENT)));

        imageDefinitionRepository.deleteImageDefinitionById(id);
        log.info("Image definition '{}' deleted successfully", id);
    }
}
