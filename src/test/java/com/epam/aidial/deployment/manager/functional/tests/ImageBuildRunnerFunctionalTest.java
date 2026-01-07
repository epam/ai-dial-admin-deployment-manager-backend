package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.kubernetes.JobCallback;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.kubernetes.NewLogJobCallback;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.service.ImageBuildRunner;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class ImageBuildRunnerFunctionalTest {

    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private ImageBuildRunner imageBuildRunner;
    @Autowired
    private JobRunner jobRunner;

    @MockitoSpyBean
    private DisposableResourceManager disposableResourceManager;

    @ParameterizedTest
    @MethodSource("getNonMcpImageDefinitions")
    public void shouldSuccessfullyBuildNonMcpImageWithDockerSource(ImageDefinition imageDefinition) {
        // Given
        var imageDef = imageDefinitionService.createImageDefinition(imageDefinition);
        var imageDefinitionId = imageDef.getId();

        when(jobRunner.run(any(JobSpecification.class), any(JobCallback.class), anyInt(), any(UUID.class), anyList(), anyList()))
                .thenReturn(true);

        // When
        imageBuildRunner.buildImage(imageDefinitionId);

        // Then
        ArgumentCaptor<String> imageNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(disposableResourceManager).saveContainerRegistryResource(imageNameCaptor.capture(), any(), any());

        var tempDisposableResources = disposableResourceManager.getAllTemporaryByGroupId(imageDefinitionId);
        Assertions.assertTrue(tempDisposableResources.isEmpty());

        var maybeRetrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId);
        Assertions.assertTrue(maybeRetrievedImageDef.isPresent());

        var retrievedImageDef = maybeRetrievedImageDef.get();
        Assertions.assertEquals(ImageStatus.BUILD_SUCCESSFUL, retrievedImageDef.getBuildStatus());
        Assertions.assertEquals(imageNameCaptor.getValue(), retrievedImageDef.getImageName());
        Assertions.assertFalse(retrievedImageDef.getBuildLogs().isEmpty());
    }

    @Test
    public void shouldFailBuildInterceptorImageWithGitSource() {
        // Given
        var imageDefToBeSaved = FunctionalTestHelper.createInterceptorImageDefinition();
        imageDefToBeSaved.setName(imageDefToBeSaved.getName() + RandomStringUtils.secure().nextAlphabetic(6).toLowerCase());
        imageDefToBeSaved.setSource(FunctionalTestHelper.createGitImageSource());

        var imageDef = imageDefinitionService.createImageDefinition(imageDefToBeSaved);
        var imageDefinitionId = imageDef.getId();

        when(jobRunner.run(any(JobSpecification.class), any(JobCallback.class), anyInt(), any(UUID.class), anyList(), anyList()))
                .thenReturn(false);

        // When
        var exception = Assertions.assertThrows(
                NotImplementedException.class,
                () -> imageBuildRunner.buildImage(imageDefinitionId)
        );

        // Then
        String expectedMessage = "Image build is not implemented for InterceptorImageDefinition image definition and GitDockerfileImageSource source yet";
        Assertions.assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    public void shouldSuccessfullyBuildMcpImageWithDockerSource() {
        // Given
        var imageDefToBeSaved = (McpImageDefinition) FunctionalTestHelper.createMcpImageDefinition();
        imageDefToBeSaved.setName(imageDefToBeSaved.getName() + "1");
        imageDefToBeSaved.setTransportType(McpTransportType.REMOTE);

        var imageDef = imageDefinitionService.createImageDefinition(imageDefToBeSaved);
        var imageDefinitionId = imageDef.getId();

        when(jobRunner.run(any(JobSpecification.class), any(JobCallback.class), anyInt(), any(UUID.class), anyList(), anyList()))
                .thenReturn(true);

        // When
        imageBuildRunner.buildImage(imageDefinitionId);

        // Then
        ArgumentCaptor<String> imageNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(disposableResourceManager).saveContainerRegistryResource(imageNameCaptor.capture(), any(), any());

        var tempDisposableResources = disposableResourceManager.getAllTemporaryByGroupId(imageDefinitionId);
        Assertions.assertTrue(tempDisposableResources.isEmpty());

        var maybeRetrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId);
        Assertions.assertTrue(maybeRetrievedImageDef.isPresent());

        var retrievedImageDef = maybeRetrievedImageDef.get();
        Assertions.assertEquals(ImageStatus.BUILD_SUCCESSFUL, retrievedImageDef.getBuildStatus());
        Assertions.assertEquals(imageNameCaptor.getValue(), retrievedImageDef.getImageName());

        Assertions.assertFalse(retrievedImageDef.getBuildLogs().isEmpty());
    }

    @Test
    public void shouldSuccessfullyBuildMcpImageWithGitSource() {
        // Given
        var imageDefToBeSaved = (McpImageDefinition) FunctionalTestHelper.createMcpImageDefinition();
        imageDefToBeSaved.setTransportType(McpTransportType.REMOTE);
        imageDefToBeSaved.setName(imageDefToBeSaved.getName() + "2");
        imageDefToBeSaved.setSource(FunctionalTestHelper.createGitImageSource());

        var imageDef = imageDefinitionService.createImageDefinition(imageDefToBeSaved);
        var imageDefinitionId = imageDef.getId();

        when(jobRunner.run(any(JobSpecification.class), any(JobCallback.class), anyInt(), any(UUID.class), anyList(), anyList()))
                .thenReturn(true);

        // When
        imageBuildRunner.buildImage(imageDefinitionId);

        // Then
        ArgumentCaptor<String> imageNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(disposableResourceManager).saveContainerRegistryResource(imageNameCaptor.capture(), any(), any());

        var tempDisposableResources = disposableResourceManager.getAllTemporaryByGroupId(imageDefinitionId);
        Assertions.assertTrue(tempDisposableResources.isEmpty());

        var maybeRetrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId);
        Assertions.assertTrue(maybeRetrievedImageDef.isPresent());

        var retrievedImageDef = maybeRetrievedImageDef.get();
        Assertions.assertEquals(ImageStatus.BUILD_SUCCESSFUL, retrievedImageDef.getBuildStatus());
        Assertions.assertEquals(imageNameCaptor.getValue(), retrievedImageDef.getImageName());
        Assertions.assertFalse(retrievedImageDef.getBuildLogs().isEmpty());
    }

    @Test
    public void shouldSuccessfullyBuildMcpImageWithGitSourceAndStdioTransport() {
        // Given
        var imageDefToBeSaved = FunctionalTestHelper.createMcpImageDefinition();
        imageDefToBeSaved.setName(imageDefToBeSaved.getName() + RandomStringUtils.secure().nextAlphabetic(6).toLowerCase());
        imageDefToBeSaved.setSource(FunctionalTestHelper.createGitImageSource());
        imageDefToBeSaved.setTransportType(McpTransportType.LOCAL);

        var imageDef = imageDefinitionService.createImageDefinition(imageDefToBeSaved);
        var imageDefinitionId = imageDef.getId();

        // Simulate the behavior of JobRunner to invoke the callback with logs
        ArgumentCaptor<NewLogJobCallback> callbackCaptor = ArgumentCaptor.forClass(NewLogJobCallback.class);
        when(jobRunner.run(any(JobSpecification.class), callbackCaptor.capture(), anyInt(), any(UUID.class), anyList(), anyList())).thenAnswer(invocation -> {
            var callback = callbackCaptor.getValue();
            callback.onNewLog(List.of("ID: test-id", "VERSION: test-version"));
            return true;
        });

        // When
        imageBuildRunner.buildImage(imageDefinitionId);

        // Then
        ArgumentCaptor<String> imageNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(disposableResourceManager, times(2)).saveContainerRegistryResource(imageNameCaptor.capture(), any(), any());

        var tempDisposableResources = disposableResourceManager.getAllTemporaryByGroupId(imageDefinitionId);
        Assertions.assertTrue(tempDisposableResources.isEmpty());

        var maybeRetrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId);
        Assertions.assertTrue(maybeRetrievedImageDef.isPresent());

        var retrievedImageDef = maybeRetrievedImageDef.get();
        Assertions.assertEquals(ImageStatus.BUILD_SUCCESSFUL, retrievedImageDef.getBuildStatus());
        Assertions.assertEquals(imageNameCaptor.getValue(), retrievedImageDef.getImageName());
    }

    @Test
    public void shouldSuccessfullyBuildMcpImageWithDockerSourceAndStdioTransport() {
        // Given
        var imageDefToBeSaved = (McpImageDefinition) FunctionalTestHelper.createMcpImageDefinition();
        imageDefToBeSaved.setTransportType(McpTransportType.LOCAL);

        var imageDef = imageDefinitionService.createImageDefinition(imageDefToBeSaved);
        var imageDefinitionId = imageDef.getId();

        // Simulate the behavior of JobRunner to invoke the callback with logs
        ArgumentCaptor<NewLogJobCallback> callbackCaptor = ArgumentCaptor.forClass(NewLogJobCallback.class);
        when(jobRunner.run(any(JobSpecification.class), callbackCaptor.capture(), anyInt(), any(UUID.class), anyList(), anyList())).thenAnswer(invocation -> {
            var callback = callbackCaptor.getValue();
            callback.onNewLog(List.of("ID: test-id", "VERSION: test-version"));
            return true;
        });

        // When
        imageBuildRunner.buildImage(imageDefinitionId);

        // Then
        ArgumentCaptor<String> imageNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(disposableResourceManager).saveContainerRegistryResource(imageNameCaptor.capture(), any(), any());

        var tempDisposableResources = disposableResourceManager.getAllTemporaryByGroupId(imageDefinitionId);
        Assertions.assertTrue(tempDisposableResources.isEmpty());

        var maybeRetrievedImageDef = imageDefinitionService.getImageDefinition(imageDefinitionId);
        Assertions.assertTrue(maybeRetrievedImageDef.isPresent());

        var retrievedImageDef = maybeRetrievedImageDef.get();
        Assertions.assertEquals(ImageStatus.BUILD_SUCCESSFUL, retrievedImageDef.getBuildStatus());
        Assertions.assertEquals(imageNameCaptor.getValue(), retrievedImageDef.getImageName());
    }

    @ParameterizedTest
    @MethodSource("getParamsForIncompleteDistroInfo")
    public void shouldFailBuildMcpImageWithDockerSourceAndStdioTransportIfDistroInfoIsNotComplete(String suffix, List<String> logs, String expectedExMessage) {
        // Given
        var imageDefToBeSaved = (McpImageDefinition) FunctionalTestHelper.createMcpImageDefinition();
        imageDefToBeSaved.setTransportType(McpTransportType.LOCAL);
        imageDefToBeSaved.setName(imageDefToBeSaved.getName() + suffix);

        var imageDef = imageDefinitionService.createImageDefinition(imageDefToBeSaved);

        // Simulate the behavior of JobRunner to invoke the callback with logs
        ArgumentCaptor<NewLogJobCallback> callbackCaptor = ArgumentCaptor.forClass(NewLogJobCallback.class);
        when(jobRunner.run(any(JobSpecification.class), callbackCaptor.capture(), anyInt(), any(UUID.class), anyList(), anyList())).thenAnswer(invocation -> {
            var callback = callbackCaptor.getValue();
            callback.onNewLog(logs);
            return true;
        });

        // When
        imageBuildRunner.buildImage(imageDef.getId());

        // Then
        var maybeRetrievedImageDef = imageDefinitionService.getImageDefinition(imageDef.getId());
        Assertions.assertTrue(maybeRetrievedImageDef.isPresent());

        var retrievedImageDef = maybeRetrievedImageDef.get();
        Assertions.assertEquals(ImageStatus.BUILD_FAILED, retrievedImageDef.getBuildStatus());
        Assertions.assertTrue(retrievedImageDef.getBuildLogs().stream().anyMatch(log -> log.contains("Image build has failed: " + expectedExMessage)));
    }

    private static Stream<Arguments> getNonMcpImageDefinitions() {
        return Stream.of(
                Arguments.of(FunctionalTestHelper.createInterceptorImageDefinition()),
                Arguments.of(FunctionalTestHelper.createAdapterImageDefinition())
        );
    }

    private static Stream<Arguments> getParamsForIncompleteDistroInfo() {
        return Stream.of(
                Arguments.of("5", List.of("VERSION: test-version"), "Distro id is not found"),
                Arguments.of("6", List.of("ID: test-id"), "Distro version is not found")
        );
    }
}
