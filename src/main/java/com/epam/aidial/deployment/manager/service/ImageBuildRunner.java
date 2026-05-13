package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ApplicationImageDefinition;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.service.pipeline.ImageBuildFromGitPipeline;
import com.epam.aidial.deployment.manager.service.pipeline.ImageCopyPipeline;
import com.epam.aidial.deployment.manager.service.pipeline.ImageWrapperBuildPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ImageBuildRunner {

    private final ImageDefinitionService imageDefinitionService;

    @Qualifier("pipeline-runner")
    private final ExecutorService executorService;

    private final ImageBuildFromGitPipeline imageBuildFromGitPipeline;
    private final ImageWrapperBuildPipeline imageWrapperBuildPipeline;
    private final ImageCopyPipeline imageCopyPipeline;

    public ImageDefinition buildImage(UUID imageDefinitionId) {
        var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found: %s".formatted(imageDefinitionId)));

        if (imageDefinition.getBuildStatus() == ImageStatus.BUILD_SUCCESSFUL || imageDefinition.getBuildStatus() == ImageStatus.BUILDING) {
            throw new IllegalArgumentException("Image '%s' is already built or build process is running".formatted(imageDefinitionId));
        }

        if (imageDefinition instanceof McpImageDefinition mcpImageDefinition) {
            return buildMcpImage(mcpImageDefinition);

        } else if (imageDefinition instanceof AdapterImageDefinition
                || imageDefinition instanceof InterceptorImageDefinition
                || imageDefinition instanceof ApplicationImageDefinition) {
            Consumer<UUID> pipeline = getPipeline(imageDefinition);
            return startDockerImagePipeline(imageDefinition, pipeline);

        } else {
            throw new NotImplementedException("Image build is not implemented for %s image definition yet"
                    .formatted(imageDefinition.getClass().getSimpleName()));
        }
    }

    private Consumer<UUID> getPipeline(ImageDefinition imageDefinition) {
        var imageSource = imageDefinition.getSource();
        Consumer<UUID> pipeline;
        if (imageSource instanceof DockerImageSource) {
            pipeline = imageCopyPipeline::run;
        } else if (imageSource instanceof GitDockerfileImageSource) {
            pipeline = imageBuildFromGitPipeline::run;
        } else {
            throw new NotImplementedException("Image build is not implemented for %s image definition and %s source yet"
                    .formatted(imageDefinition.getClass().getSimpleName(), imageSource.getClass().getSimpleName()));
        }
        return pipeline;
    }

    private ImageDefinition buildMcpImage(McpImageDefinition imageDefinition) {
        var imageSource = imageDefinition.getSource();
        Consumer<UUID> pipeline;
        if (imageSource instanceof DockerImageSource
                && imageDefinition.getTransportType() == McpTransportType.REMOTE) {
            pipeline = imageCopyPipeline::run;
        } else if (imageSource instanceof DockerImageSource
                && imageDefinition.getTransportType() == McpTransportType.LOCAL) {
            pipeline = imageWrapperBuildPipeline::run;
        } else if (imageSource instanceof GitDockerfileImageSource) {
            pipeline = imageBuildFromGitPipeline::run;
        } else {
            throw new NotImplementedException("Image build is not implemented for source %s and %s transport type yet"
                    .formatted(imageSource.getClass().getSimpleName(), imageDefinition.getTransportType()));
        }
        return startDockerImagePipeline(imageDefinition, pipeline);
    }

    private ImageDefinition startDockerImagePipeline(ImageDefinition imageDefinition, Consumer<UUID> pipeline) {
        imageDefinition.setBuildStatus(ImageStatus.BUILDING);
        imageDefinitionService.startBuild(imageDefinition.getId());

        executorService.execute(() -> pipeline.accept(imageDefinition.getId()));

        return imageDefinition;
    }

}
