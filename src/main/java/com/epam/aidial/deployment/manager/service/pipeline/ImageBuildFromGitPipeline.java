package com.epam.aidial.deployment.manager.service.pipeline;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.pipeline.step.BaseImageBuildStep;
import com.epam.aidial.deployment.manager.service.pipeline.step.ImageAnalysisStep;
import com.epam.aidial.deployment.manager.service.pipeline.step.WrapperImageBuildStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    public void run(String imageDefinitionId) {
        log.debug("run. imageDefinitionId: {}", imageDefinitionId);

        var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(imageDefinitionId)));

        try {
            run(imageDefinition);
        } catch (Exception e) {
            imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_FAILED);
            imageDefinitionService.addBuildLog(imageDefinitionId, "Image build has failed: %s".formatted(e.getMessage()));
        } finally {
            disposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId);
        }
    }

    private void run(ImageDefinition imageDefinition) {
        log.debug("run. imageDefinition: {}", imageDefinition);

        if (!(imageDefinition.getSource() instanceof GitDockerfileImageSource imageSource)) {
            throw new IllegalStateException("Image definition source is not GitDockerfileImageSource");
        }

        String imageName;
        var mcpImageDef = (McpImageDefinition) imageDefinition;

        if (mcpImageDef.getTransportType() == McpTransportType.REMOTE) {
            imageName = baseImageBuildStep.build(imageDefinition, imageSource, ResourceLifecycleState.STABLE);
        } else if (mcpImageDef.getTransportType() == McpTransportType.LOCAL) {
            var baseImageName = baseImageBuildStep.build(imageDefinition, imageSource, ResourceLifecycleState.TEMPORARY);
            var distroInfo = imageAnalysisStep.analyse(imageDefinition, baseImageName);
            imageName = wrapperImageBuildStep.build(imageDefinition, imageSource.getEntrypoint(), baseImageName, distroInfo);
        } else {
            throw new IllegalArgumentException("Unexpected MCP Image transport type: " + mcpImageDef.getTransportType());
        }

        imageDefinitionService.updateBuildStatus(imageDefinition.getId(), ImageStatus.BUILD_SUCCESSFUL);
        imageDefinitionService.setImageName(imageDefinition.getId(), imageName);
        imageDefinitionService.setBuiltAt(imageDefinition.getId(), System.currentTimeMillis());
    }

}
