package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@LogExecution
@RequiredArgsConstructor
class DeploymentCleaner {

    private final DeploymentRepository deploymentRepository;
    private final DisposableResourceManager resourceManager;
    private final DisposableResourceCleaner resourceCleaner;

    @Transactional
    void delete(UUID id) {
        resourceManager.markResourcesForCleanupByGroupId(id);
        resourceCleaner.cleanAllCleanableByGroupId(id);
        deploymentRepository.deleteById(id);
    }

}
