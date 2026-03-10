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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

import java.time.Instant;

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
        Assertions.assertEquals(ImageStatus.NOT_BUILT, createdImageDef.getBuildStatus());
        Assertions.assertNull(createdImageDef.getImageName());
        Assertions.assertNull(createdImageDef.getBuiltAt());
        Assertions.assertTrue(CollectionUtils.isEmpty(createdImageDef.getBuildLogs()));
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
        Assertions.assertEquals(ImageStatus.BUILDING, retrievedImageDef.getBuildStatus());
        Assertions.assertFalse(retrievedImageDef.getBuildLogs().isEmpty());
        Assertions.assertTrue(retrievedImageDef.getBuildLogs().stream()
                .anyMatch(log -> log.contains("Image build started")));
    }

    @Test
    public void shouldAccumulateBuildLogsDuringBuild() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // When - Add logs manually to simulate build process
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILDING);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log line 1");
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log line 2");
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log line 3");

        // Then
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        var logs = retrievedImageDef.getBuildLogs();
        Assertions.assertEquals(3, logs.size());
        Assertions.assertEquals("Log line 1", logs.get(0));
        Assertions.assertEquals("Log line 2", logs.get(1));
        Assertions.assertEquals("Log line 3", logs.get(2));
    }

    @Test
    public void shouldSetImageNameAndBuiltAtOnSuccessfulBuild() throws InterruptedException {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();
        var testImageName = "test-registry/test-image:1.0.0";
        var testTimestamp = System.currentTimeMillis();

        // When
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILDING);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Build started");
        imageDefinitionService.addBuildLog(imageDefinitionId, "Build completed");
        imageDefinitionService.setImageName(imageDefinitionId, testImageName);
        imageDefinitionService.setBuiltAt(imageDefinitionId, testTimestamp);
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_SUCCESSFUL);

        // Then
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        Assertions.assertEquals(ImageStatus.BUILD_SUCCESSFUL, retrievedImageDef.getBuildStatus());
        Assertions.assertEquals(testImageName, retrievedImageDef.getImageName());
        Assertions.assertEquals(Instant.ofEpochMilli(testTimestamp), retrievedImageDef.getBuiltAt());
        Assertions.assertEquals(2, retrievedImageDef.getBuildLogs().size());
    }

    @Test
    public void shouldPreventUpdateWhenBuildStatusIsSuccessful() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Set build status to successful
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_SUCCESSFUL);
        imageDefinitionService.setImageName(imageDefinitionId, "test-image:1.0.0");

        // When - Try to update
        var updatedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        updatedImageDef.setName("updated-name");

        // Then - Should throw exception
        var exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> imageDefinitionService.updateImageDefinition(imageDefinitionId, updatedImageDef)
        );
        Assertions.assertTrue(exception.getMessage().contains("Cannot update image definition with status BUILD_SUCCESSFUL"));
    }

    @Test
    public void shouldPreventUpdateWhenBuildStatusIsBuilding() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Set build status to successful
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILDING);
        imageDefinitionService.setImageName(imageDefinitionId, "test-image:1.0.0");

        // When - Try to update
        var updatedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        updatedImageDef.setName("updated-name");

        // Then - Should throw exception
        var exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> imageDefinitionService.updateImageDefinition(imageDefinitionId, updatedImageDef)
        );
        Assertions.assertTrue(exception.getMessage().contains("Cannot update image definition with status BUILDING"));
    }

    @Test
    public void shouldAllowUpdateWhenBuildStatusIsNotSuccessful() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Ensure status is NOT_BUILT or BUILDING
        Assertions.assertEquals(ImageStatus.NOT_BUILT, imageDef.getBuildStatus());

        // When - Update
        imageDef.setName("updated-name");
        imageDef.setDescription("updated-description");
        var updatedImageDef = imageDefinitionService.updateImageDefinition(imageDefinitionId, imageDef);

        // Then - Should succeed
        Assertions.assertEquals("updated-name", updatedImageDef.getName());
        Assertions.assertEquals("updated-description", updatedImageDef.getDescription());
    }

    @Test
    public void shouldAllowUpdateWhenBuildStatusIsFailed() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // Set build status to BUILD_FAILED
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_FAILED);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Build failed: test error");

        // When - Update
        var updatedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        updatedImageDef.setName("updated-name");
        var result = imageDefinitionService.updateImageDefinition(imageDefinitionId, updatedImageDef);

        // Then - Should succeed & reset build status
        Assertions.assertEquals("updated-name", result.getName());
        Assertions.assertEquals(ImageStatus.NOT_BUILT, result.getBuildStatus());
    }

    @Test
    public void shouldTransitionThroughAllBuildStatuses() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // When - Transition through statuses
        Assertions.assertEquals(ImageStatus.NOT_BUILT, imageDef.getBuildStatus());

        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILDING);
        var buildingDef = imageDefinitionService.getImageDefinition(imageDefinitionId).orElseThrow();
        Assertions.assertEquals(ImageStatus.BUILDING, buildingDef.getBuildStatus());

        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_SUCCESSFUL);
        var successfulDef = imageDefinitionService.getImageDefinition(imageDefinitionId).orElseThrow();
        Assertions.assertEquals(ImageStatus.BUILD_SUCCESSFUL, successfulDef.getBuildStatus());
        Assertions.assertTrue(successfulDef.getBuildStatus().isFinal());
    }

    @Test
    public void shouldPreserveBuildLogsAfterStatusTransition() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // When - Add logs and transition status
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILDING);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log 1");
        imageDefinitionService.addBuildLog(imageDefinitionId, "Log 2");
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_SUCCESSFUL);

        // Then
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        Assertions.assertEquals(ImageStatus.BUILD_SUCCESSFUL, retrievedImageDef.getBuildStatus());
        Assertions.assertEquals(2, retrievedImageDef.getBuildLogs().size());
        Assertions.assertEquals("Log 1", retrievedImageDef.getBuildLogs().get(0));
        Assertions.assertEquals("Log 2", retrievedImageDef.getBuildLogs().get(1));
    }

    @Test
    public void shouldHandleBuildFailureWithLogs() {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(FunctionalTestHelper.createMcpImageDefinition());
        var imageDefinitionId = imageDef.getId();

        // When - Simulate build failure
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILDING);
        imageDefinitionService.addBuildLog(imageDefinitionId, "Build started");
        imageDefinitionService.addBuildLog(imageDefinitionId, "Build error occurred");
        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_FAILED);

        // Then
        var retrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId)
                .orElseThrow();
        Assertions.assertEquals(ImageStatus.BUILD_FAILED, retrievedImageDef.getBuildStatus());
        Assertions.assertTrue(retrievedImageDef.getBuildStatus().isFinal());
        Assertions.assertEquals(2, retrievedImageDef.getBuildLogs().size());
        Assertions.assertNull(retrievedImageDef.getImageName()); // Should not have image name on failure
    }
}

