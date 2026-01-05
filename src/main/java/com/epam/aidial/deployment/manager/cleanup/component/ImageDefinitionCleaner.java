package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.dao.repository.ImageDefinitionRepository;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@LogExecution
@RequiredArgsConstructor
class ImageDefinitionCleaner {

    private final ImageDefinitionRepository imageDefinitionRepository;
    private final DeploymentRepository deploymentRepository;
    @Lazy
    private final ComponentCleaner componentCleaner;
    private final DisposableResourceManager resourceManager;
    private final DisposableResourceCleaner resourceCleaner;

    @Transactional
    void delete(UUID id) {
        imageDefinitionRepository.getImageDefinitionForUpdateById(id);

        resourceManager.markResourcesForCleanupByGroupId(id);
        resourceCleaner.cleanAllCleanableByGroupId(id);

        // Clean up deployments that reference this image definition
        deploymentRepository.getAllByImageDefinitionId(id).stream()
                .map(Deployment::getId)
                .forEach(deploymentId
                        -> componentCleaner.deleteAsync(ComponentRemoval.of(deploymentId, ComponentType.DEPLOYMENT)));

        imageDefinitionRepository.deleteImageDefinitionById(id);
    }

}
