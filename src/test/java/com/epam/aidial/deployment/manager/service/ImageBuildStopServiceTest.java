package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.ImageBuildNotInProgressException;
import com.epam.aidial.deployment.manager.exception.ImageBuildStopFailedException;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageBuildStopServiceTest {

    @Mock
    private ImageDefinitionService imageDefinitionService;
    @Mock
    private JobRunner jobRunner;
    @Mock
    private DisposableResourceCleaner disposableResourceCleaner;
    @Mock
    private DisposableResourceManager disposableResourceManager;

    @InjectMocks
    private ImageBuildStopService imageBuildStopService;

    private UUID imageDefinitionId;
    private McpImageDefinition imageDefinition;

    @BeforeEach
    void setUp() {
        imageDefinitionId = UUID.randomUUID();
        imageDefinition = new McpImageDefinition();
        imageDefinition.setId(imageDefinitionId);
        imageDefinition.setBuildStatus(ImageStatus.BUILDING);
    }

    @Test
    void shouldStopRunningBuild() {
        when(imageDefinitionService.getImageDefinition(imageDefinitionId))
                .thenReturn(Optional.of(imageDefinition));
        when(disposableResourceManager.getAllTemporaryByGroupId(imageDefinitionId.toString()))
                .thenReturn(List.of());
        when(imageDefinitionService.stopBuild(imageDefinitionId)).thenReturn(true);

        imageBuildStopService.stopBuild(imageDefinitionId);

        verify(jobRunner).deleteJob(imageDefinitionId);
        verify(disposableResourceCleaner).cleanTemporaryByGroupId(imageDefinitionId);
        verify(disposableResourceManager).getAllTemporaryByGroupId(imageDefinitionId.toString());
        verify(imageDefinitionService).stopBuild(imageDefinitionId);
    }

    @Test
    void shouldFailStopBuild_whenImageDefinitionNotFound() {
        when(imageDefinitionService.getImageDefinition(imageDefinitionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageBuildStopService.stopBuild(imageDefinitionId))
                .isInstanceOf(EntityNotFoundException.class);

        verify(jobRunner, never()).deleteJob(any());
        verify(disposableResourceCleaner, never()).cleanTemporaryByGroupId(any());
        verify(imageDefinitionService, never()).stopBuild(any());
    }

    @Test
    void shouldFailStopBuild_whenBuildIsNotInProgress() {
        imageDefinition.setBuildStatus(ImageStatus.BUILD_SUCCESSFUL);
        when(imageDefinitionService.getImageDefinition(imageDefinitionId))
                .thenReturn(Optional.of(imageDefinition));

        assertThatThrownBy(() -> imageBuildStopService.stopBuild(imageDefinitionId))
                .isInstanceOfSatisfying(ImageBuildNotInProgressException.class, ex ->
                        assertThat(ex.getMessage()).contains("BUILD_SUCCESSFUL"));

        verify(jobRunner, never()).deleteJob(any());
        verify(disposableResourceCleaner, never()).cleanTemporaryByGroupId(any());
        verify(imageDefinitionService, never()).stopBuild(any());
    }

    @Test
    void shouldFailStopBuild_whenClusterDeletionFails() {
        when(imageDefinitionService.getImageDefinition(imageDefinitionId))
                .thenReturn(Optional.of(imageDefinition));
        var cause = new RuntimeException("cluster API error");
        doThrow(cause).when(jobRunner).deleteJob(imageDefinitionId);

        assertThatThrownBy(() -> imageBuildStopService.stopBuild(imageDefinitionId))
                .isInstanceOfSatisfying(ImageBuildStopFailedException.class, ex -> {
                    assertThat(ex.getMessage()).startsWith("Image build could not be stopped:");
                    assertThat(ex.getMessage()).contains("cluster API error");
                });

        verify(disposableResourceCleaner, never()).cleanTemporaryByGroupId(any());
        verify(imageDefinitionService, never()).stopBuild(any());
    }

    @Test
    void shouldFailStopBuild_whenCleanupLeavesResidualResources() {
        when(imageDefinitionService.getImageDefinition(imageDefinitionId))
                .thenReturn(Optional.of(imageDefinition));
        when(disposableResourceManager.getAllTemporaryByGroupId(imageDefinitionId.toString()))
                .thenReturn(List.of(new DisposableResource()));

        assertThatThrownBy(() -> imageBuildStopService.stopBuild(imageDefinitionId))
                .isInstanceOfSatisfying(ImageBuildStopFailedException.class, ex -> {
                    assertThat(ex.getMessage()).startsWith("Image build could not be stopped:");
                    assertThat(ex.getMessage()).contains("not fully removed");
                });

        verify(jobRunner).deleteJob(imageDefinitionId);
        verify(disposableResourceCleaner).cleanTemporaryByGroupId(imageDefinitionId);
        verify(imageDefinitionService, never()).stopBuild(any());
    }

    @Test
    void shouldFailStopBuild_whenPipelineWonTheRace() {
        when(imageDefinitionService.getImageDefinition(imageDefinitionId))
                .thenReturn(Optional.of(imageDefinition));
        when(disposableResourceManager.getAllTemporaryByGroupId(imageDefinitionId.toString()))
                .thenReturn(List.of());
        when(imageDefinitionService.stopBuild(imageDefinitionId)).thenReturn(false);
        var latestDefinition = new McpImageDefinition();
        latestDefinition.setId(imageDefinitionId);
        latestDefinition.setBuildStatus(ImageStatus.BUILD_SUCCESSFUL);
        when(imageDefinitionService.getImageDefinition(imageDefinitionId))
                .thenReturn(Optional.of(imageDefinition))
                .thenReturn(Optional.of(latestDefinition));

        assertThatThrownBy(() -> imageBuildStopService.stopBuild(imageDefinitionId))
                .isInstanceOfSatisfying(ImageBuildNotInProgressException.class, ex ->
                        assertThat(ex.getMessage()).contains("BUILD_SUCCESSFUL"));

        verify(jobRunner).deleteJob(imageDefinitionId);
        verify(disposableResourceCleaner).cleanTemporaryByGroupId(imageDefinitionId);
    }
}
