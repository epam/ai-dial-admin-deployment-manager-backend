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

        boolean externallyInterrupted = false;
        try {
            run(imageDefinition);
        } catch (JobExternallyDeletedException e) {
            externallyInterrupted = true;
            log.info("Image copy pipeline interrupted by external Job deletion for image definition '{}'. "
                    + "Leaving status change and cleanup to the stop action.", imageDefinitionId);
        } catch (Exception e) {
            log.error("Image copy pipeline failed for image definition {}", imageDefinitionId, e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            imageDefinitionService.failBuild(imageDefinitionId, "Image copying has failed: %s".formatted(detail));
        } finally {
            if (!externallyInterrupted) {
                disposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId);
            }
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
