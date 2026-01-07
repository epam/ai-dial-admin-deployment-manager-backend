package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
abstract class AbstractCleanupStrategy implements CleanupStrategy {

    protected final DisposableResourceManager resourceManager;
    protected final DisposableResourceCleaner resourceCleaner;

    @Transactional
    protected void cleanupResources(UUID groupId) {
        resourceManager.markResourcesForCleanupByGroupId(groupId);
        resourceCleaner.cleanAllCleanableByGroupId(groupId);
    }
}
