package com.epam.aidial.deployment.manager.service.pipeline;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.JobExternallyDeletedException;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.pipeline.step.ImageCopyStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class ImageCopyPipeline {

    private final ImageDefinitionService imageDefinitionService;
    private final DisposableResourceCleaner disposableResourceCleaner;

    private final ImageCopyStep imageCopyStep;

    public void run(UUID imageDefinitionId) {
        var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(imageDefinitionId)));

        try {
            run(imageDefinition);
        } catch (JobExternallyDeletedException e) {
            log.info("Image copy pipeline interrupted by external Job deletion for image definition '{}'. "
                    + "Leaving status change to the stop action.", imageDefinitionId);
        } catch (Exception e) {
            imageDefinitionService.failBuild(imageDefinitionId, "Image copying has failed: %s".formatted(e.getMessage()));
        } finally {
            disposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId);
        }
    }

    private void run(ImageDefinition imageDefinition) {
        if (!(imageDefinition.getSource() instanceof DockerImageSource dockerImageSource)) {
            throw new IllegalStateException("Image definition source is not DockerImageSource");
        }

        var imageName = imageCopyStep.copy(imageDefinition, dockerImageSource.getImageUri());
        imageDefinitionService.completeBuildSuccessfully(imageDefinition.getId(), imageName, System.currentTimeMillis());
    }

}
