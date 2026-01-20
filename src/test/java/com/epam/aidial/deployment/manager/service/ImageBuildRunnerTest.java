package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.service.pipeline.ImageBuildFromGitPipeline;
import com.epam.aidial.deployment.manager.service.pipeline.ImageCopyPipeline;
import com.epam.aidial.deployment.manager.service.pipeline.ImageWrapperBuildPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageBuildRunnerTest {

    @Mock
    private ImageDefinitionService imageDefinitionService;
    @Mock
    private ExecutorService executorService;
    @Mock
    private ImageBuildFromGitPipeline imageBuildFromGitPipeline;
    @Mock
    private ImageWrapperBuildPipeline imageWrapperBuildPipeline;
    @Mock
    private ImageCopyPipeline imageCopyPipeline;

    @Test
    void buildImage_resetsLogsBeforeStartingNewBuild() {
        var imageDefinitionId = UUID.randomUUID().toString();
        var imageDefinition = McpImageDefinition.builder()
                .id(imageDefinitionId)
                .buildStatus(ImageStatus.NOT_BUILT)
                .transportType(McpTransportType.REMOTE)
                .source(new DockerImageSource("dummy", Collections.emptyList()))
                .version("1.0.0")
                .build();

        when(imageDefinitionService.getImageDefinition(imageDefinitionId))
                .thenReturn(Optional.of(imageDefinition));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        var runner = new ImageBuildRunner(
                imageDefinitionService,
                executorService,
                imageBuildFromGitPipeline,
                imageWrapperBuildPipeline,
                imageCopyPipeline
        );

        runner.buildImage(imageDefinitionId);

        InOrder inOrder = inOrder(imageDefinitionService);
        inOrder.verify(imageDefinitionService).updateBuildStatus(imageDefinitionId, ImageStatus.BUILDING);
        inOrder.verify(imageDefinitionService).resetBuildLogs(imageDefinitionId);
        inOrder.verify(imageDefinitionService).addBuildLog(imageDefinitionId, "Image build started");

        verify(executorService).execute(any(Runnable.class));
        verify(imageCopyPipeline).run(imageDefinitionId);
    }
}
