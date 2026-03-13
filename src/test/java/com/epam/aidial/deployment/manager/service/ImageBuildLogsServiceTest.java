package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked"})
@ExtendWith(MockitoExtension.class)
class ImageBuildLogsServiceTest {

    private static final UUID IMAGE_DEFINITION_ID = UUID.randomUUID();
    @Spy
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    @Mock
    private ImageDefinitionService imageDefinitionService;
    @Mock
    private SseEmitterFactory sseEmitterFactory;
    @Mock
    private SseEmitter sseEmitter;
    private ImageBuildLogsService imageBuildLogsService;

    @Captor
    private ArgumentCaptor<Function<SseEmitter, SafeAutoCloseable>> emitterConsumerCaptor;

    private ImageDefinition testImageDefinition;
    private SafeAutoCloseable capturedSafeAutoCloseable;

    @BeforeEach
    void setUp() {
        // Create service instance manually with small poll interval for testing
        imageBuildLogsService = new ImageBuildLogsService(
                imageDefinitionService,
                sseEmitterFactory,
                executorService,
                10L,  // Small poll interval for faster tests
                10L // Small min streaming interval for faster tests
        );

        testImageDefinition = McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .buildStatus(ImageStatus.BUILDING)
                .buildLogs(new ArrayList<>(List.of("Log line 1", "Log line 2")))
                .version("1.0.0")
                .build();

        when(sseEmitterFactory.createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                anyString(),
                (Function<SseEmitter, SafeAutoCloseable>) any(Function.class)
        )).thenReturn(sseEmitter);

        lenient().when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID))
                .thenReturn(Optional.of(testImageDefinition));

        capturedSafeAutoCloseable = null;
    }

    @AfterEach
    void tearDown() {
        if (capturedSafeAutoCloseable != null) {
            try {
                capturedSafeAutoCloseable.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Disabled("Floating test, run manually")
    @SneakyThrows
    void streamLogs_shouldCreateEmitterAndStartLogStreaming() {
        // When
        SseEmitter result = imageBuildLogsService.streamLogs(IMAGE_DEFINITION_ID);

        // Then
        assertThat(result).isEqualTo(sseEmitter);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-log"),
                emitterConsumerCaptor.capture()
        );

        // Simulate the emitter initialization
        capturedSafeAutoCloseable = emitterConsumerCaptor.getValue().apply(sseEmitter);

        CountDownLatch latch = new CountDownLatch(2);
        doAnswer((Answer<Void>) invocation -> {
            latch.countDown();
            return null;
        }).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
        // Wait for events to be sent
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

        // Verify that ImageDefinitionService was called to get build logs
        verify(imageDefinitionService, times(1)).getImageDefinition(IMAGE_DEFINITION_ID);
    }

    @Test
    void streamAccessedDomains_shouldCreateEmitterWithCorrectKey() {
        SseEmitter result = imageBuildLogsService.streamAccessedDomains(IMAGE_DEFINITION_ID);

        assertThat(result).isEqualTo(sseEmitter);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-accessed-domains"),
                emitterConsumerCaptor.capture()
        );
    }

    @Test
    @SneakyThrows
    void streamStatus_shouldCreateEmitterAndStartStatusStreaming() {
        // Given
        var updatedImageDefinition = McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .buildStatus(ImageStatus.BUILD_SUCCESSFUL) //final status for only one cycle in streaming loop
                .build();

        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID))
                .thenReturn(Optional.of(updatedImageDefinition));

        // When
        SseEmitter result = imageBuildLogsService.streamStatus(IMAGE_DEFINITION_ID);

        // Then
        assertThat(result).isEqualTo(sseEmitter);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-status"),
                emitterConsumerCaptor.capture()
        );

        // Simulate the emitter initialization
        capturedSafeAutoCloseable = emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Wait for events to be sent
        Thread.sleep(100);

        // Verify that ImageDefinitionService was called to get build status
        verify(imageDefinitionService, times(1)).getImageDefinition(IMAGE_DEFINITION_ID);
    }

    @Test
    @SneakyThrows
    void streamLogs_shouldSendLogsAndStatus() {
        // Given
        var updatedImageDefinition = McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .buildStatus(ImageStatus.BUILD_SUCCESSFUL)
                .buildLogs(new ArrayList<>(List.of("Log line 1", "Log line 2", "Log line 3")))
                .version("1.0.0")
                .build();

        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID))
                .thenReturn(Optional.of(updatedImageDefinition));

        CountDownLatch latch = new CountDownLatch(4);
        doAnswer((Answer<Void>) invocation -> {
            latch.countDown();
            return null;
        }).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        // When
        imageBuildLogsService.streamLogs(IMAGE_DEFINITION_ID);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-log"),
                emitterConsumerCaptor.capture()
        );
        capturedSafeAutoCloseable = emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Wait for events to be sent
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

        // Verify that log events were sent
        verify(sseEmitter, times(4)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @SneakyThrows
    void streamStatus_shouldSendStatusUpdates() {
        // Given
        var updatedImageDefinition = McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .buildStatus(ImageStatus.BUILD_SUCCESSFUL)
                .buildLogs(new ArrayList<>(List.of("Log line 1", "Log line 2")))
                .version("1.0.0")
                .build();

        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID))
                .thenReturn(Optional.of(testImageDefinition))
                .thenReturn(Optional.of(updatedImageDefinition));

        CountDownLatch latch = new CountDownLatch(2);
        doAnswer((Answer<Void>) invocation -> {
            latch.countDown();
            return null;
        }).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        // When
        imageBuildLogsService.streamStatus(IMAGE_DEFINITION_ID);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-status"),
                emitterConsumerCaptor.capture()
        );
        capturedSafeAutoCloseable = emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Wait for events to be sent
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

        // Verify that status events were sent
        verify(sseEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @SneakyThrows
    void streamLogs_shouldCompleteEmitterWhenImageReachesFinalState() {
        // Given
        var finalImageDefinition = McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .buildStatus(ImageStatus.BUILD_SUCCESSFUL)
                .buildLogs(new ArrayList<>(List.of("Log line 1", "Log line 2")))
                .version("1.0.0")
                .build();

        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID))
                .thenReturn(Optional.of(finalImageDefinition));

        CountDownLatch completionLatch = new CountDownLatch(1);
        doAnswer((Answer<Void>) invocation -> null).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
        doAnswer(invocation -> {
            completionLatch.countDown();
            return null;
        }).when(sseEmitter).complete();

        // When
        imageBuildLogsService.streamLogs(IMAGE_DEFINITION_ID);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-log"),
                emitterConsumerCaptor.capture()
        );
        capturedSafeAutoCloseable = emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Wait for completion
        assertThat(completionLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Then
        verify(sseEmitter).complete();
    }

    @Test
    @SneakyThrows
    void streamLogs_shouldHandleExceptionsDuringEventSending() {
        // Given
        doAnswer((Answer<Void>) invocation -> {
            throw new IOException("Test exception");
        }).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        CountDownLatch errorLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            errorLatch.countDown();
            return null;
        }).when(sseEmitter).completeWithError(any(Exception.class));

        // When
        imageBuildLogsService.streamLogs(IMAGE_DEFINITION_ID);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-log"),
                emitterConsumerCaptor.capture()
        );
        capturedSafeAutoCloseable = emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Wait for error handling
        assertThat(errorLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Then
        // Verify that the emitter was completed with error
        verify(sseEmitter).completeWithError(any(Exception.class));
    }

    @Test
    @SneakyThrows
    void streamLogs_shouldHandleImmediateCompletionForFinalState() {
        // Given
        var finalImageDefinition = McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .buildStatus(ImageStatus.BUILD_SUCCESSFUL)
                .buildLogs(new ArrayList<>(List.of("Log line 1", "Log line 2")))
                .version("1.0.0")
                .build();

        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID))
                .thenReturn(Optional.of(finalImageDefinition));

        CountDownLatch completionLatch = new CountDownLatch(1);
        doAnswer((Answer<Void>) invocation -> null).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
        doAnswer(invocation -> {
            completionLatch.countDown();
            return null;
        }).when(sseEmitter).complete();

        // When
        imageBuildLogsService.streamLogs(IMAGE_DEFINITION_ID);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-log"),
                emitterConsumerCaptor.capture()
        );
        capturedSafeAutoCloseable = emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Wait for completion
        assertThat(completionLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Then
        // Verify that the emitter was completed
        verify(sseEmitter).complete();
    }

    @Test
    @SneakyThrows
    void streamStatus_shouldHandleMultipleStatusUpdates() {
        // Given
        var buildingImageDefinition = McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .buildStatus(ImageStatus.BUILDING)
                .version("1.0.0")
                .build();

        var failedImageDefinition = McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .buildStatus(ImageStatus.BUILD_FAILED)
                .version("1.0.0")
                .build();

        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID))
                .thenReturn(Optional.of(testImageDefinition))
                .thenReturn(Optional.of(buildingImageDefinition))
                .thenReturn(Optional.of(failedImageDefinition));

        CountDownLatch sendLatch = new CountDownLatch(2);
        CountDownLatch completionLatch = new CountDownLatch(1);
        doAnswer((Answer<Void>) invocation -> {
            sendLatch.countDown();
            return null;
        }).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
        doAnswer(invocation -> {
            completionLatch.countDown();
            return null;
        }).when(sseEmitter).complete();

        // When
        imageBuildLogsService.streamStatus(IMAGE_DEFINITION_ID);
        verify(sseEmitterFactory).createEmitter(
                eq(IMAGE_DEFINITION_ID.toString()),
                eq("ImageBuild-status"),
                emitterConsumerCaptor.capture()
        );
        capturedSafeAutoCloseable = emitterConsumerCaptor.getValue().apply(sseEmitter);

        // Wait for status updates and completion
        assertThat(sendLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(completionLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Then
        // Verify that status events were sent
        verify(sseEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(sseEmitter).complete();
    }
}