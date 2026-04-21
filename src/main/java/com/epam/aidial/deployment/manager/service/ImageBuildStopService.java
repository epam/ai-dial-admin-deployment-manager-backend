package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.ImageBuildNotInProgressException;
import com.epam.aidial.deployment.manager.exception.ImageBuildStopFailedException;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ImageBuildStopService {

    private final ImageDefinitionService imageDefinitionService;
    private final JobRunner jobRunner;
    private final DisposableResourceCleaner disposableResourceCleaner;
    private final DisposableResourceManager disposableResourceManager;

    public void stopBuild(UUID imageDefinitionId) {
        var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Image definition not found by id: %s".formatted(imageDefinitionId)));

        var currentStatus = imageDefinition.getBuildStatus();
        if (currentStatus != ImageStatus.BUILDING) {
            throw new ImageBuildNotInProgressException(currentStatus);
        }

        try {
            jobRunner.deleteJob(imageDefinitionId);
        } catch (Exception cause) {
            log.warn("Failed to delete build Job for image definition '{}': {}", imageDefinitionId, cause.getMessage(), cause);
            throw new ImageBuildStopFailedException(cause);
        }

        disposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId);
        var remaining = disposableResourceManager.getAllTemporaryByGroupId(String.valueOf(imageDefinitionId));
        if (!remaining.isEmpty()) {
            var cause = new IllegalStateException(
                    "Cluster resources not fully removed for groupId %s: %d remaining".formatted(imageDefinitionId, remaining.size()));
            log.warn("Stop aborted — {} disposable resource(s) still TEMPORARY after cleanup for image definition '{}'",
                    remaining.size(), imageDefinitionId);
            throw new ImageBuildStopFailedException(cause);
        }

        boolean stopped = imageDefinitionService.stopBuild(imageDefinitionId);
        if (!stopped) {
            var latestStatus = imageDefinitionService.getImageDefinition(imageDefinitionId)
                    .map(ImageDefinition::getBuildStatus)
                    .orElse(ImageStatus.NOT_BUILT);
            log.info("Stop arrived after pipeline completion for image definition '{}': current status {}",
                    imageDefinitionId, latestStatus);
            throw new ImageBuildNotInProgressException(latestStatus);
        }
        log.info("Image build stopped for image definition '{}'", imageDefinitionId);
    }
}
