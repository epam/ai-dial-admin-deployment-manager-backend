package com.epam.aidial.deployment.manager.service.pipeline;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.pipeline.step.ImageCopyStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@LogExecution
@RequiredArgsConstructor
public class ImageCopyPipeline {

    private final ImageDefinitionService imageDefinitionService;
    private final DisposableResourceCleaner disposableResourceCleaner;

    private final ImageCopyStep imageCopyStep;

    public void run(String imageDefinitionId) {
        var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(imageDefinitionId)));

        try {
            run(imageDefinition);
        } catch (Exception e) {
            imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_FAILED);
            imageDefinitionService.addBuildLog(imageDefinitionId, "Image copying has failed: %s".formatted(e.getMessage()));
        } finally {
            disposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId);
        }
    }

    private void run(ImageDefinition imageDefinition) {
        if (!(imageDefinition.getSource() instanceof DockerImageSource dockerImageSource)) {
            throw new IllegalStateException("Image definition source is not DockerImageSource");
        }

        var imageName = imageCopyStep.copy(imageDefinition, dockerImageSource.getImageUri());
        imageDefinitionService.updateBuildStatus(imageDefinition.getId(), ImageStatus.BUILD_SUCCESSFUL);
        imageDefinitionService.setImageName(imageDefinition.getId(), imageName);
        imageDefinitionService.setBuiltAt(imageDefinition.getId(), System.currentTimeMillis());
    }

}
