package com.epam.aidial.deployment.manager.service.pipeline;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.JobExternallyDeletedException;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.pipeline.step.ImageAnalysisStep;
import com.epam.aidial.deployment.manager.service.pipeline.step.WrapperImageBuildStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class ImageWrapperBuildPipeline {

    private final ImageDefinitionService imageDefinitionService;
    private final DisposableResourceCleaner disposableResourceCleaner;

    private final ImageAnalysisStep imageAnalysisStep;
    private final WrapperImageBuildStep wrapperImageBuildStep;

    public void run(UUID imageDefinitionId) {
        var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(imageDefinitionId)));

        boolean externallyInterrupted = false;
        try {
            run(imageDefinition);
        } catch (JobExternallyDeletedException e) {
            externallyInterrupted = true;
            log.info("Image build pipeline interrupted by external Job deletion for image definition '{}'. "
                    + "Leaving status change and cleanup to the stop action.", imageDefinitionId);
        } catch (Exception e) {
            imageDefinitionService.failBuild(imageDefinitionId, "Image build has failed: %s".formatted(e.getMessage()));
        } finally {
            if (!externallyInterrupted) {
                disposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId);
            }
        }
    }

    private void run(ImageDefinition imageDefinition) {
        if (!(imageDefinition.getSource() instanceof DockerImageSource imageSource)) {
            throw new IllegalStateException("Image definition source is not DockerImageSource");
        }

        var distroInfo = imageAnalysisStep.analyse(imageDefinition, imageSource.getImageUri());
        var wrapperImageName = wrapperImageBuildStep.build(imageDefinition, imageSource.getEntrypoint(), imageSource.getImageUri(), distroInfo);
        imageDefinitionService.completeBuildSuccessfully(imageDefinition.getId(), wrapperImageName, System.currentTimeMillis());
    }

}
