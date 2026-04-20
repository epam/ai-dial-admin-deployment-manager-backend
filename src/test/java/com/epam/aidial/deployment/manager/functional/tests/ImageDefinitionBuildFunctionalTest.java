package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.service.ImageBuildRunner;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.pipeline.ImageCopyPipeline;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Functional tests for ImageDefinition-based build process.
 * Tests build status transitions, build logs accumulation, and build metadata updates.
 */
public abstract class ImageDefinitionBuildFunctionalTest {

    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private ImageBuildRunner imageBuildRunner;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;

    @BeforeEach
    void setUp() {
        Mockito.clearInvocations(securityClaimsExtractor);
    }

    @Test
    public void shouldStartWithNotBuiltStatusOnCreation() {
        // Given
        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();

        // When
        var createdImageDef = imageDefinitionService.createImageDefinition(imageDef);

        // Then
        assertThat(createdImageDef.getBuildStatus()).isEqualTo(ImageStatus.NOT_BUILT);
        assertThat(createdImageDef.getImageName()).isNull();
        assertThat(createdImageDef.getBuiltAt()).isNull();
        assertThat(CollectionUtils.isEmpty(createdImageDef.getBuildLogs())).isTrue();
    }

    @Test
    @SneakyThrows
    public void shouldTransitionToBuildingStatusWhenBuildStarts() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Mock ImageCopyPipeline.run to not execute actual logic
        // Unwrap the AOP proxy to access the real ImageBuildRunner target
        ImageCopyPipeline mockCopyPipeline = mock(ImageCopyPipeline.class);
        var targetRunner = AopTestUtils.getTargetObject(imageBuildRunner);
        var imageCopyPipelineField = targetRunner.getClass().getDeclaredField("imageCopyPipeline");
        imageCopyPipelineField.setAccessible(true);
        imageCopyPipelineField.set(targetRunner, mockCopyPipeline);
        Mockito.doNothing().when(mockCopyPipeline).run(imageDefinitionId);

        // When
        imageBuildRunner.buildImage(imageDefinitionId);

        // Then - Check immediately after build starts
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        assertThat(retrievedImageDef.getBuildStatus()).isEqualTo(ImageStatus.BUILDING);
        assertThat(retrievedImageDef.getBuildLogs().isEmpty()).isFalse();
        assertThat(retrievedImageDef.getBuildLogs().stream()
                .anyMatch(log -> log.contains("Image build started"))).isTrue();
    }

    @Test
    public void shouldAccumulateBuildLogsDuringBuild() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // When - startBuild adds "Image build started", then add more logs manually
        imageDefinitionService.startBuild(imageDefinitionId);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log line 1");
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log line 2");
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log line 3");

        // Then - test env has a build log size limit of 3, so the oldest entry is trimmed
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        var logs = retrievedImageDef.getBuildLogs();
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0)).isEqualTo("Log line 1");
        assertThat(logs.get(1)).isEqualTo("Log line 2");
        assertThat(logs.get(2)).isEqualTo("Log line 3");
    }

    @Test
    public void shouldSetImageNameAndBuiltAtOnSuccessfulBuild() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();
        var testImageName = "test-registry/test-image:1.0.0";
        var testTimestamp = System.currentTimeMillis();

        // When - simulate full build lifecycle
        imageDefinitionService.startBuild(imageDefinitionId);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Build in progress");
        imageDefinitionService.completeBuildSuccessfully(imageDefinitionId, testImageName, testTimestamp);

        // Then
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        assertThat(retrievedImageDef.getBuildStatus()).isEqualTo(ImageStatus.BUILD_SUCCESSFUL);
        assertThat(retrievedImageDef.getImageName()).isEqualTo(testImageName);
        assertThat(retrievedImageDef.getBuiltAt()).isEqualTo(Instant.ofEpochMilli(testTimestamp));
        assertThat(retrievedImageDef.getBuildLogs()).hasSize(2);
    }

    @Test
    public void shouldPreventUpdateWhenBuildStatusIsSuccessful() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Set build status to successful
        imageDefinitionService.completeBuildSuccessfully(imageDefinitionId, "test-image:1.0.0", System.currentTimeMillis());

        // When - Try to update
        var updatedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        updatedImageDef.setName("updated-name");

        // Then - Should throw exception
        assertThatThrownBy(() -> imageDefinitionService.updateImageDefinition(imageDefinitionId, updatedImageDef))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update image definition with status BUILD_SUCCESSFUL");
    }

    @Test
    public void shouldPreventUpdateWhenBuildStatusIsBuilding() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Set build status to building
        imageDefinitionService.startBuild(imageDefinitionId);

        // When - Try to update
        var updatedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        updatedImageDef.setName("updated-name");

        // Then - Should throw exception
        assertThatThrownBy(() -> imageDefinitionService.updateImageDefinition(imageDefinitionId, updatedImageDef))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update image definition with status BUILDING");
    }

    @Test
    public void shouldAllowUpdateWhenBuildStatusIsNotSuccessful() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Ensure status is NOT_BUILT or BUILDING
        assertThat(imageDef.getBuildStatus()).isEqualTo(ImageStatus.NOT_BUILT);

        // When - Update
        imageDef.setName("updated-name");
        imageDef.setDescription("updated-description");
        var updatedImageDef = imageDefinitionService.updateImageDefinition(imageDefinitionId, imageDef);

        // Then - Should succeed
        assertThat(updatedImageDef.getName()).isEqualTo("updated-name");
        assertThat(updatedImageDef.getDescription()).isEqualTo("updated-description");
    }

    @Test
    public void shouldAllowUpdateWhenBuildStatusIsFailed() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Set build status to BUILD_FAILED
        imageDefinitionService.failBuild(imageDefinitionId, "Build failed: test error");

        // When - Update
        var updatedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        updatedImageDef.setName("updated-name");
        var result = imageDefinitionService.updateImageDefinition(imageDefinitionId, updatedImageDef);

        // Then - Should succeed & reset build status
        assertThat(result.getName()).isEqualTo("updated-name");
        assertThat(result.getBuildStatus()).isEqualTo(ImageStatus.NOT_BUILT);
    }

    @Test
    public void shouldTransitionThroughAllBuildStatuses() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // When - Transition through statuses
        assertThat(imageDef.getBuildStatus()).isEqualTo(ImageStatus.NOT_BUILT);

        imageDefinitionService.startBuild(imageDefinitionId);
        var buildingDef = imageDefinitionService.getImageDefinition(imageDefinitionId).orElseThrow();
        assertThat(buildingDef.getBuildStatus()).isEqualTo(ImageStatus.BUILDING);

        imageDefinitionService.completeBuildSuccessfully(imageDefinitionId, "test-image", System.currentTimeMillis());
        var successfulDef = imageDefinitionService.getImageDefinition(imageDefinitionId).orElseThrow();
        assertThat(successfulDef.getBuildStatus()).isEqualTo(ImageStatus.BUILD_SUCCESSFUL);
        assertThat(successfulDef.getBuildStatus().isFinal()).isTrue();
    }

    @Test
    public void shouldPreserveBuildLogsAfterStatusTransition() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // When - startBuild adds "Image build started", then add more logs and complete
        imageDefinitionService.startBuild(imageDefinitionId);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log 1");
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log 2");
        imageDefinitionService.completeBuildSuccessfully(imageDefinitionId, "test-image", System.currentTimeMillis());

        // Then - all logs preserved across status transitions
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        assertThat(retrievedImageDef.getBuildStatus()).isEqualTo(ImageStatus.BUILD_SUCCESSFUL);
        assertThat(retrievedImageDef.getBuildLogs()).hasSize(3);
        assertThat(retrievedImageDef.getBuildLogs().get(0)).isEqualTo("Image build started");
        assertThat(retrievedImageDef.getBuildLogs().get(1)).isEqualTo("Log 1");
        assertThat(retrievedImageDef.getBuildLogs().get(2)).isEqualTo("Log 2");
    }

    @Test
    public void shouldHandleBuildFailureWithLogs() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // When - Simulate build failure: startBuild adds "Image build started",
        // then addBuildLog adds intermediate log, failBuild appends error and sets BUILD_FAILED
        imageDefinitionService.startBuild(imageDefinitionId);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Build error occurred");
        imageDefinitionService.failBuild(imageDefinitionId, "Image build has failed: timeout");

        // Then
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        assertThat(retrievedImageDef.getBuildStatus()).isEqualTo(ImageStatus.BUILD_FAILED);
        assertThat(retrievedImageDef.getBuildStatus().isFinal()).isTrue();
        assertThat(retrievedImageDef.getBuildLogs()).hasSize(3);
        assertThat(retrievedImageDef.getImageName()).isNull();
    }
}

