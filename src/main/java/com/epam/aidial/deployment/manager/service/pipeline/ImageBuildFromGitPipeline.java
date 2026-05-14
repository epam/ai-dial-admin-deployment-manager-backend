package com.epam.aidial.deployment.manager.service.pipeline;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.JobExternallyDeletedException;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.pipeline.step.BaseImageBuildStep;
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
public class ImageBuildFromGitPipeline {

    private final ImageDefinitionService imageDefinitionService;
    private final DisposableResourceCleaner disposableResourceCleaner;

    private final BaseImageBuildStep baseImageBuildStep;
    private final ImageAnalysisStep imageAnalysisStep;
    private final WrapperImageBuildStep wrapperImageBuildStep;

    public void run(UUID imageDefinitionId) {
        log.debug("run. imageDefinitionId: {}", imageDefinitionId);

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
        log.debug("run. imageDefinition: {}", imageDefinition);

        if (!(imageDefinition.getSource() instanceof GitDockerfileImageSource imageSource)) {
            throw new IllegalStateException("Image definition source is not GitDockerfileImageSource");
        }

        String imageName;
        if (imageDefinition instanceof McpImageDefinition mcpImageDef
                && mcpImageDef.getTransportType() == McpTransportType.LOCAL) {
            var baseImageName = baseImageBuildStep.build(imageDefinition, imageSource, ResourceLifecycleState.TEMPORARY);
            var distroInfo = imageAnalysisStep.analyse(imageDefinition, baseImageName);
            imageName = wrapperImageBuildStep.build(imageDefinition, imageSource.getEntrypoint(), baseImageName, distroInfo);
        } else {
            imageName = baseImageBuildStep.build(imageDefinition, imageSource, ResourceLifecycleState.STABLE);
        }

        imageDefinitionService.completeBuildSuccessfully(imageDefinition.getId(), imageName, System.currentTimeMillis());
    }

}
